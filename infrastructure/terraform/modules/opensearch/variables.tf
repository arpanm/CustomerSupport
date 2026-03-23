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
  description = "VPC ID where the OpenSearch domain will be deployed"
  type        = string
}

variable "subnet_ids" {
  description = "List of subnet IDs for the OpenSearch domain (private subnets)"
  type        = list(string)
}

variable "eks_security_group_id" {
  description = "Security group ID of the EKS cluster/nodes allowed to connect to OpenSearch"
  type        = string
}

variable "instance_type" {
  description = "OpenSearch instance type for data nodes"
  type        = string
  default     = "t3.small.search"
}

variable "instance_count" {
  description = "Number of data nodes in the OpenSearch cluster (1 for dev, 3 for prod)"
  type        = number
  default     = 1
}

variable "volume_size" {
  description = "EBS volume size in GiB for each OpenSearch data node"
  type        = number
  default     = 20
}

variable "master_user_arn" {
  description = "IAM ARN to use as the OpenSearch master user (IRSA service account ARN)"
  type        = string
}

variable "tags" {
  description = "Additional tags to apply to all resources"
  type        = map(string)
  default     = {}
}
