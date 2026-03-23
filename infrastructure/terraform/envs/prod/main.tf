###############################################################################
# SupportHub – Production Environment
# Wires all modules together for the production environment
# Key differences from dev/staging:
#   - m5.large EC2 instances / db.m5.large RDS / cache.m5.large ElastiCache
#   - RDS multi_az=true, deletion_protection=true
#   - 3 Kafka broker nodes (one per AZ)
#   - OpenSearch 3-node cluster
###############################################################################

terraform {
  required_version = ">= 1.6.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
    tls = {
      source  = "hashicorp/tls"
      version = "~> 4.0"
    }
  }
}

provider "aws" {
  region = var.aws_region

  default_tags {
    tags = {
      Environment = "prod"
      Project     = "supporthub"
      ManagedBy   = "terraform"
    }
  }
}

###############################################################################
# EKS
###############################################################################

module "eks" {
  source = "../../modules/eks"

  environment    = "prod"
  vpc_id         = var.vpc_id
  subnet_ids     = var.private_subnet_ids
  instance_types = ["m5.large"]
  desired_size   = 4
  min_size       = 3
  max_size       = 10

  public_access_cidrs = ["0.0.0.0/0"]
}

###############################################################################
# RDS – PostgreSQL 16
###############################################################################

module "rds" {
  source = "../../modules/rds"

  environment           = "prod"
  vpc_id                = var.vpc_id
  subnet_ids            = var.private_subnet_ids
  eks_security_group_id = module.eks.cluster_security_group_id
  instance_class        = "db.m5.large"
  multi_az              = true
  deletion_protection   = true
  db_password           = var.db_password
}

###############################################################################
# ElastiCache – Redis 7
###############################################################################

module "elasticache" {
  source = "../../modules/elasticache"

  environment           = "prod"
  vpc_id                = var.vpc_id
  subnet_ids            = var.private_subnet_ids
  eks_security_group_id = module.eks.cluster_security_group_id
  node_type             = "cache.m5.large"
  num_cache_clusters    = 2
  auth_token            = var.redis_auth_token
}

###############################################################################
# MSK – Kafka
###############################################################################

module "msk" {
  source = "../../modules/msk"

  environment            = "prod"
  vpc_id                 = var.vpc_id
  subnet_ids             = var.private_subnet_ids
  eks_security_group_id  = module.eks.cluster_security_group_id
  number_of_broker_nodes = 3
  instance_type          = "kafka.m5.large"
}

###############################################################################
# S3 – Attachments
###############################################################################

module "s3" {
  source = "../../modules/s3"

  environment          = "prod"
  bucket_suffix        = var.s3_bucket_suffix
  kms_key_id           = var.kms_key_id
  cors_allowed_origins = ["https://supporthub.in", "https://www.supporthub.in"]
}

###############################################################################
# ECR – Container Registries
###############################################################################

module "ecr" {
  source = "../../modules/ecr"

  environment = "prod"
}

###############################################################################
# OpenSearch
###############################################################################

module "opensearch" {
  source = "../../modules/opensearch"

  environment           = "prod"
  vpc_id                = var.vpc_id
  subnet_ids            = var.private_subnet_ids
  eks_security_group_id = module.eks.cluster_security_group_id
  instance_type         = "m5.large.search"
  instance_count        = 3
  master_user_arn       = var.opensearch_master_user_arn
}
