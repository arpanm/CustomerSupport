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
  description = "VPC ID where the MSK cluster will be deployed"
  type        = string
}

variable "subnet_ids" {
  description = "List of subnet IDs for MSK broker nodes (one per AZ, private subnets)"
  type        = list(string)
}

variable "eks_security_group_id" {
  description = "Security group ID of the EKS cluster/nodes allowed to connect to MSK"
  type        = string
}

variable "kafka_version" {
  description = "Apache Kafka version for the MSK cluster"
  type        = string
  default     = "3.5.1"
}

variable "number_of_broker_nodes" {
  description = "Number of broker nodes in the MSK cluster (must match number of subnets for multi-AZ)"
  type        = number
  default     = 3
}

variable "instance_type" {
  description = "MSK broker instance type"
  type        = string
  default     = "kafka.t3.small"
}

variable "tags" {
  description = "Additional tags to apply to all resources"
  type        = map(string)
  default     = {}
}
