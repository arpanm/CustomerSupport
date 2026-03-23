###############################################################################
# SupportHub – Dev Environment
# Wires all modules together for the dev (development) environment
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
      Environment = "dev"
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

  environment    = "dev"
  vpc_id         = var.vpc_id
  subnet_ids     = var.private_subnet_ids
  instance_types = ["t3.medium"]
  desired_size   = 2
  min_size       = 1
  max_size       = 4

  public_access_cidrs = ["0.0.0.0/0"]
}

###############################################################################
# RDS – PostgreSQL 16
###############################################################################

module "rds" {
  source = "../../modules/rds"

  environment           = "dev"
  vpc_id                = var.vpc_id
  subnet_ids            = var.private_subnet_ids
  eks_security_group_id = module.eks.cluster_security_group_id
  instance_class        = "db.t3.medium"
  multi_az              = false
  deletion_protection   = false
  db_password           = var.db_password
}

###############################################################################
# ElastiCache – Redis 7
###############################################################################

module "elasticache" {
  source = "../../modules/elasticache"

  environment           = "dev"
  vpc_id                = var.vpc_id
  subnet_ids            = var.private_subnet_ids
  eks_security_group_id = module.eks.cluster_security_group_id
  node_type             = "cache.t3.micro"
  num_cache_clusters    = 1
  auth_token            = var.redis_auth_token
}

###############################################################################
# MSK – Kafka
###############################################################################

module "msk" {
  source = "../../modules/msk"

  environment            = "dev"
  vpc_id                 = var.vpc_id
  subnet_ids             = [var.private_subnet_ids[0]]
  eks_security_group_id  = module.eks.cluster_security_group_id
  number_of_broker_nodes = 1
  instance_type          = "kafka.t3.small"
}

###############################################################################
# S3 – Attachments
###############################################################################

module "s3" {
  source = "../../modules/s3"

  environment          = "dev"
  bucket_suffix        = var.s3_bucket_suffix
  cors_allowed_origins = ["http://localhost:3000", "http://localhost:3001", "http://localhost:3002"]
}

###############################################################################
# ECR – Container Registries
###############################################################################

module "ecr" {
  source = "../../modules/ecr"

  environment = "dev"
}

###############################################################################
# OpenSearch
###############################################################################

module "opensearch" {
  source = "../../modules/opensearch"

  environment           = "dev"
  vpc_id                = var.vpc_id
  subnet_ids            = var.private_subnet_ids
  eks_security_group_id = module.eks.cluster_security_group_id
  instance_type         = "t3.small.search"
  instance_count        = 1
  master_user_arn       = var.opensearch_master_user_arn
}
