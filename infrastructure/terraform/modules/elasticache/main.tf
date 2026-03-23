###############################################################################
# SupportHub – ElastiCache (Redis 7) Module
# Creates: Redis replication group, subnet group, security group
###############################################################################

locals {
  identifier = "${var.project}-${var.environment}"
  tags = merge(var.tags, {
    Environment = var.environment
    Project     = var.project
    ManagedBy   = "terraform"
  })
}

###############################################################################
# Security Group
###############################################################################

resource "aws_security_group" "redis" {
  name        = "${local.identifier}-redis-sg"
  description = "Security group for ElastiCache Redis – allow port 6379 from EKS nodes only"
  vpc_id      = var.vpc_id

  ingress {
    description     = "Redis from EKS nodes"
    from_port       = 6379
    to_port         = 6379
    protocol        = "tcp"
    security_groups = [var.eks_security_group_id]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
    description = "Allow all outbound"
  }

  tags = merge(local.tags, { Name = "${local.identifier}-redis-sg" })
}

###############################################################################
# Subnet Group
###############################################################################

resource "aws_elasticache_subnet_group" "supporthub" {
  name        = "${local.identifier}-redis-subnet-group"
  description = "Subnet group for SupportHub ElastiCache Redis"
  subnet_ids  = var.subnet_ids
  tags        = local.tags
}

###############################################################################
# Redis Replication Group
###############################################################################

resource "aws_elasticache_replication_group" "supporthub" {
  replication_group_id = "${local.identifier}-redis"
  description          = "SupportHub Redis cache – ${var.environment}"

  engine               = "redis"
  engine_version       = "7.0"
  node_type            = var.node_type
  num_cache_clusters   = var.num_cache_clusters
  parameter_group_name = "default.redis7"
  port                 = 6379

  subnet_group_name  = aws_elasticache_subnet_group.supporthub.name
  security_group_ids = [aws_security_group.redis.id]

  at_rest_encryption_enabled  = true
  transit_encryption_enabled  = true
  auth_token                  = var.auth_token
  auth_token_update_strategy  = "ROTATE"

  automatic_failover_enabled = var.num_cache_clusters > 1 ? true : false
  multi_az_enabled           = var.num_cache_clusters > 1 ? true : false

  snapshot_retention_limit = 1
  snapshot_window          = "03:00-04:00"
  maintenance_window       = "mon:04:00-mon:05:00"

  auto_minor_version_upgrade = true
  apply_immediately          = var.environment == "dev" ? true : false

  tags = local.tags
}
