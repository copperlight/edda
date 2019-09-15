/*
 * Copyright 2012-2019 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.edda.util

import com.typesafe.scalalogging.StrictLogging

import scala.util.matching.Regex
import scala.util.parsing.combinator._

// https://developer.linkedin.com/documents/field-selectors

object FieldSelectorExpr extends StrictLogging {

  case class Result(objectMatches: Boolean, newValue: Option[Any])

  val NoMatch = Result(objectMatches = false, None)
}

import com.netflix.edda.util.FieldSelectorExpr._

sealed trait FieldSelectorExpr {

  def select(value: Any): Option[Any] = {
    _select(value) match {
      case Result(true, result)   => result
      case Result(false, Some(_)) => None
      case NoMatch                => Some(value)
    }
  }

  def _select(value: Any): Result = value match {
    case Nil =>
      Result(objectMatches = true, Some(value))

    case values: Seq[_] =>
      val results = values
        .map(checkValue)
        .filter { r =>
          r.objectMatches && r.newValue.isDefined
        }

      Result(objectMatches = true, Some(results.map(_.newValue.get)))

    case v =>
      checkValue(v)
  }

  def checkValue(value: Any): Result
}

case object MatchAnyExpr extends FieldSelectorExpr {

  def checkValue(value: Any): Result = {
    Result(objectMatches = true, Some(value))
  }
}

case class FixedExpr(matches: Boolean) extends FieldSelectorExpr {

  def checkValue(value: Any): Result = {
    Result(matches, Some(value))
  }
}

case class FlattenExpr(expr: FieldSelectorExpr) extends FieldSelectorExpr {

  def checkValue(value: Any): Result = {
    Result(objectMatches = true, Some(flattenValue(None, value)))
  }

  def flattenValue(prefix: Option[String], value: Any): Any = {
    value match {
      case map: Map[_, _] => flattenMap(prefix, map)
      case _              => value
    }
  }

  def flattenMap(prefix: Option[String], value: Map[_, _]): Map[String, Any] = {
    val prefixMap = value.map { t =>
      val key = t._1.toString

      val newKey = prefix match {
        case Some(p) => p + "." + key
        case None    => key
      }

      newKey -> t._2
    }

    val (subMaps, values) = prefixMap.partition(_._2.isInstanceOf[Map[_, _]])

    values ++ subMaps.flatMap { t =>
      flattenMap(Some(t._1), t._2.asInstanceOf[Map[_, _]])
    }
  }
}

case class KeySelectExpr(keys: Map[String, FieldSelectorExpr]) extends FieldSelectorExpr {

  def checkValue(value: Any): Result = value match {
    case map: Map[_, _] =>
      val newMap = map
        .filter(t => keys.contains(t._1.toString))
        .map(t => t._1 -> keys(t._1.toString)._select(t._2))

      val matches = newMap.values.forall(_.objectMatches)

      val resultMap = newMap
        .filter(_._2.newValue.isDefined)
        .map(t => t._1 -> t._2.newValue.get)

      Result(matches, Some(resultMap))

    case _ =>
      NoMatch
  }
}

case class EqualExpr(desiredValue: Any) extends FieldSelectorExpr {

  def checkValue(value: Any): Result = {
    Result(value == desiredValue, Some(value))
  }
}

case class NotEqualExpr(desiredValue: Any) extends FieldSelectorExpr {

  def checkValue(value: Any): Result = {
    Result(value != desiredValue, Some(value))
  }
}

case class RegexExpr(regex: String, invert: Boolean) extends FieldSelectorExpr {

  import java.util.regex.Pattern

  private val pattern = Pattern.compile(regex)

  def checkValue(value: Any): Result = {
    val matches = pattern.matcher(value.toString).find
    Result(matches ^ invert, Some(value))
  }
}

object FieldSelectorParser {

  def parse(expr: String): FieldSelectorExpr = {
    val parser = new FieldSelectorParser
    parser.parseExpr(expr)
  }
}

class FieldSelectorParser extends RegexParsers {

  def expression: Parser[FieldSelectorExpr] = flattenExpr | keySelectExpr

  def keySelectExpr: Parser[KeySelectExpr] =
    ":(" ~> repsep(subExpr, ",") <~ ")" ^^ { values =>
      KeySelectExpr(Map.empty ++ values.map { t =>
        t._1 -> t._2.getOrElse(FixedExpr(matches = true))
      })
    }

  def flattenExpr: Parser[FlattenExpr] = ":" ~> keySelectExpr ^^ (value => FlattenExpr(value))

  def subExpr: Parser[~[String, Option[FieldSelectorExpr]]] =
    id ~ (equalExpr |
    notEqualExpr |
    regexExpr |
    invRegexExpr |
    expression).?

  def equalExpr: Parser[EqualExpr] = "=" ~> literalExpr ^^ (value => EqualExpr(value))

  def notEqualExpr: Parser[NotEqualExpr] = "!=" ~> literalExpr ^^ (value => NotEqualExpr(value))

  def regexExpr: Parser[RegexExpr] = "~" ~> regexLiteral ^^ (value => RegexExpr(value, invert = false))

  def invRegexExpr: Parser[RegexExpr] = "!~" ~> regexLiteral ^^ (value => RegexExpr(value, invert = true))

  /**
    * ids may have colons, such as the following fields from view/instances:
    *
    * - tags.aws:autoscaling:groupName
    * - tags.spinnaker:application
    * - tags.spinnaker:stack
    *
    * to allow for these cases without disrupting the parsing grammar, we perform two negative
    * look-aheads when matching colon characters, so that we exclude the ":(" case, which begins
    * a new keySelectExpr and the "::" case, which begins a new flattenExpr.
    *
    */
  def id: Parser[String] = regex("([a-zA-Z0-9_\\.\\-]|(?!:\\()(?!::):)*".r)

  def literalExpr: Parser[Any] =
    stringLiteral |
    nullLiteral |
    trueLiteral |
    falseLiteral |
    integerLiteral |
    floatLiteral

  def stringLiteral: Parser[String] =
    regex("\"[^\"]*\"".r) ^^ (value => {
      value.substring(1, value.length - 1)
    })

  def nullLiteral: Parser[Null] = "null" ^^ (_ => null)

  def trueLiteral: Parser[Boolean] = "true" ^^ (_ => true)

  def falseLiteral: Parser[Boolean] = "false" ^^ (_ => false)

  def integerLiteral: Parser[Int] = regex("-?[0-9]+".r) ^^ (value => value.toInt)

  def floatLiteral: Parser[Double] = regex(floatRegex) ^^ (value => value.toDouble)

  val floatRegex: Regex = "[-+]?[0-9]*\\.?[0-9]+([eE][-+]?[0-9]+)?".r

  def regexLiteral: Parser[String] = "/" ~> regex("[^/]*".r) <~ "/"

  def parseExpr(expr: String): FieldSelectorExpr = {
    def fail(expr: String, msg: String): Nothing = {
      throw new IllegalArgumentException(s"could not parse expression '$expr': $msg")
    }

    val result = parseAll(expression, expr) match {
      case Success(res, _) => res
      case Failure(msg, _) => fail(expr, msg)
      case Error(msg, _)   => fail(expr, msg)
      case _               => fail(expr, "unknown")
    }

    result
  }
}
