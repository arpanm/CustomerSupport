# SupportHub – Terraform Infrastructure

This directory contains all Terraform configuration for SupportHub's AWS infrastructure.

## Structure

```
terraform/
├── modules/          # Reusable infrastructure modules
│   ├── eks/          # EKS cluster + managed node group + IRSA
│   ├── rds/          # PostgreSQL 16 RDS instance
│   ├── elasticache/  # Redis 7 ElastiCache replication group
│   ├── msk/          # MSK Managed Kafka cluster
│   ├── s3/           # S3 bucket for file attachments
│   ├── ecr/          # ECR repositories for all 11 services
│   └── opensearch/   # OpenSearch (Elasticsearch-compatible) domain
└── envs/             # Per-environment configurations
    ├── dev/
    ├── staging/
    └── prod/
```

## Prerequisites

- Terraform >= 1.6.0
- AWS CLI configured with appropriate credentials
- S3 bucket `supporthub-terraform-state` and DynamoDB table `supporthub-terraform-locks` must exist before running `terraform init` (bootstrap these manually or with a separate script)

## Per-Environment Configuration

| Setting | dev | staging | prod |
|---|---|---|---|
| EKS nodes | t3.medium (2 desired) | t3.large (3 desired) | m5.large (4 desired) |
| RDS | db.t3.medium, single-AZ | db.t3.large, single-AZ | db.m5.large, multi-AZ |
| Redis | cache.t3.micro, 1 node | cache.t3.small, 2 nodes | cache.m5.large, 2 nodes |
| Kafka brokers | 1 (kafka.t3.small) | 2 (kafka.t3.small) | 3 (kafka.m5.large) |
| OpenSearch nodes | 1 (t3.small.search) | 2 (t3.small.search) | 3 (m5.large.search) |
| RDS deletion protection | false | false | true |
| RDS multi-AZ | false | false | true |

## Usage

### First-time setup (all environments follow the same pattern)

```bash
cd infrastructure/terraform/envs/dev

# Initialise the backend and download providers
terraform init

# Review what will be created
terraform plan -var-file="dev.tfvars"

# Apply changes
terraform apply -var-file="dev.tfvars"
```

### Required tfvars

Create a `<env>.tfvars` file (never commit to git) with the following:

```hcl
vpc_id                     = "vpc-xxxxxxxx"
private_subnet_ids         = ["subnet-aaa", "subnet-bbb", "subnet-ccc"]
public_subnet_ids          = ["subnet-ddd", "subnet-eee", "subnet-fff"]
db_password                = "<strong-password>"
redis_auth_token           = "<strong-token-min-16-chars>"
opensearch_master_user_arn = "arn:aws:iam::123456789012:role/supporthub-reporting-sa"
s3_bucket_suffix           = "123456789012"  # usually your AWS account ID

# prod only
kms_key_id = "arn:aws:kms:ap-south-1:123456789012:key/..."
```

### Targeting a specific module

```bash
terraform plan -target=module.rds
terraform apply -target=module.eks
```

### Destroying an environment

```bash
# dev/staging only — prod has deletion_protection=true on RDS
terraform destroy -var-file="dev.tfvars"
```

## Remote State

All environments store state in:

| Environment | S3 Key |
|---|---|
| dev | `dev/terraform.tfstate` |
| staging | `staging/terraform.tfstate` |
| prod | `prod/terraform.tfstate` |

State locking uses DynamoDB table `supporthub-terraform-locks`.

## Tagging

All resources are tagged with:

| Tag | Value |
|---|---|
| `Environment` | `dev` / `staging` / `prod` |
| `Project` | `supporthub` |
| `ManagedBy` | `terraform` |

## Security Notes

- Never hardcode secrets — use tfvars files (excluded from git via `.gitignore`)
- RDS passwords are marked `sensitive = true` and will not appear in plan output
- All inter-service traffic is restricted via security groups — only EKS nodes can reach RDS, Redis, Kafka, and OpenSearch
- Production S3 uses SSE-KMS; dev/staging use SSE-AES256
- ECR repositories use IMMUTABLE tags to prevent image overwrites
