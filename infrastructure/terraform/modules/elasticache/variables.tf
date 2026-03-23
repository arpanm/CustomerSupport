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
  description = "VPC ID where ElastiCache will be deployed"
  type        = string
}

variable "subnet_ids" {
  description = "List of subnet IDs for the ElastiCache subnet group (private subnets)"
  type        = list(string)
}

variable "eks_security_group_id" {
  description = "Security group ID of the EKS cluster/nodes allowed to connect to Redis"
  type        = string
}

variable "node_type" {
  description = "ElastiCache node type"
  type        = string
  default     = "cache.t3.micro"
}

variable "num_cache_clusters" {
  description = "Number of cache clusters (1 for dev, 2+ for staging/prod)"
  type        = number
  default     = 1
}

variable "auth_token" {
  description = "Auth token (password) for Redis transit encryption"
  type        = string
  sensitive   = true
}

variable "tags" {
  description = "Additional tags to apply to all resources"
  type        = map(string)
  default     = {}
}
