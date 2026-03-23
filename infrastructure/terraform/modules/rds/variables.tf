variable "project" {
  description = "Project name used for resource naming and tagging"
  type        = string
  default     = "supporthub"
}

variable "environment" {
  description = "Deployment environment (dev, staging, prod)"
  type        = string
}

variable "vpc_id" {
  description = "VPC ID where the RDS instance will be deployed"
  type        = string
}

variable "subnet_ids" {
  description = "List of subnet IDs for the RDS subnet group (private subnets recommended)"
  type        = list(string)
}

variable "eks_security_group_id" {
  description = "Security group ID of the EKS cluster/nodes, allowed to connect to RDS"
  type        = string
}

variable "instance_class" {
  description = "RDS instance class"
  type        = string
  default     = "db.t3.medium"
}

variable "db_name" {
  description = "Name of the initial database to create"
  type        = string
  default     = "supporthub"
}

variable "db_username" {
  description = "Master username for the RDS instance"
  type        = string
  default     = "supporthub_admin"
  sensitive   = true
}

variable "db_password" {
  description = "Master password for the RDS instance"
  type        = string
  sensitive   = true
}

variable "multi_az" {
  description = "Whether to enable Multi-AZ for high availability"
  type        = bool
  default     = false
}

variable "deletion_protection" {
  description = "Enable deletion protection on the RDS instance"
  type        = bool
  default     = false
}

variable "tags" {
  description = "Additional tags to apply to all resources"
  type        = map(string)
  default     = {}
}
