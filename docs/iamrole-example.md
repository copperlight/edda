## Introduction

Describes the minimum configuration required for the IAM role used by Edda.

## Quick Start

Use the `PowerUser` role as a starting point, noting that this role has the ability to write to AWS APIs. The rest of
this document describes a set of custom roles which adhere to the minimum access principle. 

## Role: Edda

The role establishes the access control policies that will apply to the application and lists which instance profiles
in what accounts will be allowed to assume into the role. The instance profiles only need to be established in the
accounts where you will run your Edda instances - typically one for test and one for prod. The Edda role needs to exist
in all of the accounts that you want to allow Edda to crawl.

```bash
aws --profile $PROFILE_NAME iam get-role --role-name Edda
```

```json
{
  "Role": {
    "Path": "/",
    "RoleName": "Edda",
    "RoleId": "$ROLE_ID",
    "Arn": "arn:aws:iam::$ACCOUNT_ID:role/Edda",
    "CreateDate": "$CREATE_DATE",
    "AssumeRolePolicyDocument": {
      "Version": "2012-10-17",
      "Statement": [
        {
          "Effect": "Allow",
          "Principal": {
            "AWS": [
              "arn:aws:iam::$ACCOUNT_ID_1:role/EddaInstanceProfile",
              "arn:aws:iam::$ACCOUNT_ID_2:role/EddaInstanceProfile"
            ]
          },
          "Action": "sts:AssumeRole"
        }
      ]
    },
    "MaxSessionDuration": 3600
  }
}
```

## Role Policies: Edda

The policies attached to the role are what allows the instance to read data from AWS APIs, after it assumes into the
role from the instance profile.

```bash
aws --profile $PROFILE_NAME iam list-role-policies --role-name Edda
```

```json
{
  "PolicyNames": [
    "AWS_ReadAccess_Edda",
    "DynamoDB_FullAccess_Edda",
    "S3_ReadWrite_Edda"
  ]
}
```

### AWS_ReadAccess_Edda

Allow the instance to read data from AWS APIs.

```bash
aws --profile $PROFILE_NAME iam get-role-policy --role-name Edda --policy-name AWS_ReadAccess_Edda
```

```json
{
  "RoleName": "Edda",
  "PolicyName": "AWS_ReadAccess_Edda",
  "PolicyDocument": {
    "Statement": [
      {
        "Action": [
          "appstream:Get*",
          "autoscaling:Describe*",
          "cloudformation:DescribeStackEvents",
          "cloudformation:DescribeStackResources",
          "cloudformation:DescribeStacks",
          "cloudformation:GetTemplate",
          "cloudformation:List*",
          "cloudfront:Get*",
          "cloudfront:List*",
          "cloudtrail:DescribeTrails",
          "cloudtrail:GetTrailStatus",
          "cloudwatch:Describe*",
          "cloudwatch:Get*",
          "cloudwatch:List*",
          "directconnect:Describe*",
          "dynamodb:BatchGetItem",
          "dynamodb:DescribeTable",
          "dynamodb:GetItem",
          "dynamodb:ListTables",
          "dynamodb:Query",
          "dynamodb:Scan",
          "ec2:Describe*",
          "elasticache:Describe*",
          "elasticbeanstalk:Check*",
          "elasticbeanstalk:Describe*",
          "elasticbeanstalk:List*",
          "elasticbeanstalk:RequestEnvironmentInfo",
          "elasticbeanstalk:RetrieveEnvironmentInfo",
          "elasticloadbalancing:Describe*",
          "elasticmapreduce:Describe*",
          "elasticmapreduce:List*",
          "elastictranscoder:List*",
          "elastictranscoder:Read*",
          "iam:Get*",
          "iam:List*",
          "lambda:Get*",
          "lambda:List*",
          "opsworks:Describe*",
          "opsworks:Get*",
          "rds:Describe*",
          "rds:ListTagsForResource",
          "redshift:Describe*",
          "redshift:ViewQueriesInConsole",
          "route53:Get*",
          "route53:List*",
          "sdb:GetAttributes",
          "sdb:List*",
          "sdb:Select*",
          "ses:Get*",
          "ses:List*",
          "sns:Get*",
          "sns:List*",
          "sqs:GetQueueAttributes",
          "sqs:ListQueues",
          "sqs:ReceiveMessage",
          "storagegateway:Describe*",
          "storagegateway:List*"
        ],
        "Effect": "Allow",
        "Resource": [
          "*"
        ]
      }
    ]
  }
}
```

### DynamoDB_FullAccess_Edda

Allow the instance full access to DynamoDB tables whose names start with `edda-`.

The application will create two tables for each Edda deployment, if they do not already exist. The tables track the
current write leader for the deployment and the latest payloads for each data collection.

```bash
aws --profile $PROFILE_NAME iam get-role-policy --role-name Edda --policy-name DynamoDB_FullAccess_Edda
```

```json
{
  "RoleName": "Edda",
  "PolicyName": "DynamoDB_FullAccess_Edda",
  "PolicyDocument": {
    "Statement": [
      {
        "Action": [
          "dynamodb:*"
        ],
        "Effect": "Allow",
        "Resource": [
          "arn:aws:dynamodb:*:$ACCOUNT_ID:table/edda-*"
        ]
      }
    ]
  }
}
```

### S3_ReadWrite_Edda

Allow the instance full access to an S3 bucket, for storing 

The S3 bucket is used to by the Edda write leader to store the latest crawl results, which are then loaded by the
followers after looking up the filename in DynamoDB.

It is recommended to have a dedicated bucket for this purpose, because Edda writes a payload for every enabled
collection every 30 seconds. The data growth should be controlled by applying a bcuket-wide lifecycle rule to
expire objects older than 30 days.

There should be one bucket per region, using the following naming convention: `edda.$REGION.$DOMAIN_NAME`.

```bash
aws --profile $PROFILE_NAME iam get-role-policy --role-name Edda --policy-name S3_ReadWrite_Edda
```

```json
{
  "RoleName": "Edda",
  "PolicyName": "S3_ReadWrite_Edda",
  "PolicyDocument": {
    "Statement": [
      {
        "Action": [
          "s3:ListBucket",
          "s3:GetBucketLocation"
        ],
        "Effect": "Allow",
        "Resource": [
          "arn:aws:s3:::edda.*.$DOMAIN_NAME"
        ]
      },
      {
        "Action": [
          "s3:DeleteObject",
          "s3:GetObject",
          "s3:PutObject",
          "s3:PutObjectAcl"
        ],
        "Effect": "Allow",
        "Resource": [
          "arn:aws:s3:::edda.*.$DOMAIN_NAME/*"
        ]
      }
    ]
  }
}
```

## Instance Profile: EddaInstanceProfile

The instance profile allows you to pass role information to an instance when it starts. It defines the base access
permissions an instance has within AWS. In the case of Edda, this role is used to grant access that allows the
application to assume the Edda role. 

```bash
aws --profile $PROFILE iam get-role --role-name EddaInstanceProfile
```

```json
{
  "Role": {
    "Path": "/",
    "RoleName": "EddaInstanceProfile",
    "RoleId": "$ROLE_ID",
    "Arn": "arn:aws:iam::$ACCOUNT_ID:role/EddaInstanceProfile",
    "CreateDate": "$CREATE_DATE",
    "AssumeRolePolicyDocument": {
      "Version": "2012-10-17",
      "Statement": [
        {
          "Effect": "Allow",
          "Principal": {
            "Service": "ec2.amazonaws.com"
          },
          "Action": "sts:AssumeRole"
        }
      ]
    },
    "MaxSessionDuration": 3600
  }
}
```

## Role Policies: EddaInstanceProfile

```bash
aws --profile $PROFILE_NAME iam list-role-policies --role-name EddaInstanceProfile
```

```json
{
  "PolicyNames": [
    "S3_ReadWrite_Logs",
    "STSAssumeRole_Edda"
  ]
}
```

### S3_ReadWrite_Logs

This policy allows the instance to rotate logs to a common log bucket in AWS.

Replace `$LOG_BUCKET_PREFIX` with the name of your log bucket, something like `logs.*.$DOMAIN_NAME`, where the log
buckets are named `logs.$REGION.$DOMAIN_NAME` so that the role is allowed to write to the log buckets in every region.

```bash
aws --profile $PROFILE_NAME iam get-role-policy --role-name EddaInstanceProfile --policy-name S3_ReadWrite_Logs
```

```json
{
  "RoleName": "EddaInstanceProfile",
  "PolicyName": "S3_ReadWrite_Logs",
  "PolicyDocument": {
    "Statement": [
      {
        "Action": [
          "s3:ListBucket"
        ],
        "Effect": "Allow",
        "Resource": [
          "arn:aws:s3:::$LOG_BUCKET"
        ]
      },
      {
        "Action": [
          "s3:GetObject",
          "s3:PutObject"
        ],
        "Effect": "Allow",
        "Resource": [
          "arn:aws:s3:::$LOG_BUCKET/*"
        ]
      }
    ]
  }
}
```

### STSAssumeRole_Edda

This policy allows instances running the EddaInstanceProfile to assume into the Edda role, in any account, as long as
that account has the Edda role defined.

```bash
aws --profile $PROFILE_NAME iam get-role-policy --role-name EddaInstanceProfile --policy-name STSAssumeRole_Edda
```

```json
{
  "RoleName": "EddaInstanceProfile",
  "PolicyName": "STSAssumeRole_Edda",
  "PolicyDocument": {
    "Statement": [
      {
        "Action": [
          "sts:AssumeRole"
        ],
        "Effect": "Allow",
        "Resource": [
          "arn:aws:iam::*:role/Edda"
        ]
      }
    ]
  }
}
```
