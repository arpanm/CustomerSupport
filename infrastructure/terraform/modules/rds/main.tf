###############################################################################
# SupportHub – RDS Module
# Creates: PostgreSQL 16 RDS instance, parameter group, subnet group, SG
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

resource "aws_security_group" "rds" {
  name        = "${local.identifier}-rds-sg"
  description = "Security group for RDS PostgreSQL – allow port 5432 from EKS nodes only"
  vpc_id      = var.vpc_id

  ingress {
    description     = "PostgreSQL from EKS nodes"
    from_port       = 5432
    to_port         = 5432
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

  tags = merge(local.tags, { Name = "${local.identifier}-rds-sg" })
}

###############################################################################
# Subnet Group
###############################################################################

resource "aws_db_subnet_group" "supporthub" {
  name        = "${local.identifier}-rds-subnet-group"
  description = "Subnet group for SupportHub RDS instance"
  subnet_ids  = var.subnet_ids
  tags        = local.tags
}

###############################################################################
# Parameter Group
###############################################################################

resource "aws_db_parameter_group" "supporthub" {
  name        = "${local.identifier}-pg16"
  family      = "postgres16"
  description = "SupportHub custom parameter group for PostgreSQL 16"

  parameter {
    name  = "shared_preload_libraries"
    value = "pg_stat_statements"
  }

  parameter {
    name  = "log_min_duration_statement"
    value = "1000"
  }

  parameter {
    name  = "log_connections"
    value = "1"
  }

  parameter {
    name  = "log_disconnections"
    value = "1"
  }

  tags = local.tags
}

###############################################################################
# RDS Instance – PostgreSQL 16
###############################################################################

resource "aws_db_instance" "supporthub" {
  identifier        = local.identifier
  engine            = "postgres"
  engine_version    = "16.1"
  instance_class    = var.instance_class
  allocated_storage = 20
  max_allocated_storage = 100
  storage_type      = "gp3"
  storage_encrypted = true

  db_name  = var.db_name
  username = var.db_username
  password = var.db_password

  parameter_group_name   = aws_db_parameter_group.supporthub.name
  db_subnet_group_name   = aws_db_subnet_group.supporthub.name
  vpc_security_group_ids = [aws_security_group.rds.id]

  multi_az                = var.multi_az
  backup_retention_period = 7
  backup_window           = "03:00-04:00"
  maintenance_window      = "Mon:04:00-Mon:05:00"

  deletion_protection       = var.deletion_protection
  skip_final_snapshot       = var.deletion_protection ? false : true
  final_snapshot_identifier = var.deletion_protection ? "${local.identifier}-final-snapshot" : null

  performance_insights_enabled          = true
  performance_insights_retention_period = 7

  enabled_cloudwatch_logs_exports = ["postgresql", "upgrade"]

  auto_minor_version_upgrade = true
  copy_tags_to_snapshot      = true

  tags = local.tags
}
