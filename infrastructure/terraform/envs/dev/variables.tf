variable "aws_region" {
  description = "AWS region for all resources"
  type        = string
  default     = "ap-south-1"
}

variable "vpc_id" {
  description = "VPC ID for the dev environment"
  type        = string
}

variable "private_subnet_ids" {
  description = "List of private subnet IDs for the dev environment"
  type        = list(string)
}

variable "public_subnet_ids" {
  description = "List of public subnet IDs for the dev environment"
  type        = list(string)
}

variable "db_password" {
  description = "Master password for the RDS PostgreSQL instance"
  type        = string
  sensitive   = true
}

variable "redis_auth_token" {
  description = "Auth token for ElastiCache Redis"
  type        = string
  sensitive   = true
}

variable "opensearch_master_user_arn" {
  description = "IAM ARN for the OpenSearch master user"
  type        = string
}

variable "s3_bucket_suffix" {
  description = "Unique suffix for S3 bucket name (e.g., account ID or random string)"
  type        = string
}
