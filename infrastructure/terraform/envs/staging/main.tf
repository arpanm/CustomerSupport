###############################################################################
# SupportHub – Staging Environment
# Wires all modules together for the staging environment
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
      Environment = "staging"
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

  environment    = "staging"
  vpc_id         = var.vpc_id
  subnet_ids     = var.private_subnet_ids
  instance_types = ["t3.large"]
  desired_size   = 3
  min_size       = 2
  max_size       = 6

  public_access_cidrs = ["0.0.0.0/0"]
}

###############################################################################
# RDS – PostgreSQL 16
###############################################################################

module "rds" {
  source = "../../modules/rds"

  environment           = "staging"
  vpc_id                = var.vpc_id
  subnet_ids            = var.private_subnet_ids
  eks_security_group_id = module.eks.cluster_security_group_id
  instance_class        = "db.t3.large"
  multi_az              = false
  deletion_protection   = false
  db_password           = var.db_password
}

###############################################################################
# ElastiCache – Redis 7
###############################################################################

module "elasticache" {
  source = "../../modules/elasticache"

  environment           = "staging"
  vpc_id                = var.vpc_id
  subnet_ids            = var.private_subnet_ids
  eks_security_group_id = module.eks.cluster_security_group_id
  node_type             = "cache.t3.small"
  num_cache_clusters    = 2
  auth_token            = var.redis_auth_token
}

###############################################################################
# MSK – Kafka
###############################################################################

module "msk" {
  source = "../../modules/msk"

  environment            = "staging"
  vpc_id                 = var.vpc_id
  subnet_ids             = slice(var.private_subnet_ids, 0, 2)
  eks_security_group_id  = module.eks.cluster_security_group_id
  number_of_broker_nodes = 2
  instance_type          = "kafka.t3.small"
}

###############################################################################
# S3 – Attachments
###############################################################################

module "s3" {
  source = "../../modules/s3"

  environment          = "staging"
  bucket_suffix        = var.s3_bucket_suffix
  cors_allowed_origins = ["https://staging.supporthub.in"]
}

###############################################################################
# ECR – Container Registries
###############################################################################

module "ecr" {
  source = "../../modules/ecr"

  environment = "staging"
}

###############################################################################
# OpenSearch
###############################################################################

module "opensearch" {
  source = "../../modules/opensearch"

  environment           = "staging"
  vpc_id                = var.vpc_id
  subnet_ids            = var.private_subnet_ids
  eks_security_group_id = module.eks.cluster_security_group_id
  instance_type         = "t3.small.search"
  instance_count        = 2
  master_user_arn       = var.opensearch_master_user_arn
}
