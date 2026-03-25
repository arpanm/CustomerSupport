variable "project" {
  description = "Project name used for resource naming and tagging"
  type        = string
  default     = "supporthub"
}

variable "environment" {
  description = "Deployment environment (dev, staging, prod)"
  type        = string
}

variable "service_names" {
  description = "List of microservice names for which to create ECR repositories"
  type        = list(string)
  default = [
    "api-gateway",
    "auth-service",
    "ticket-service",
    "customer-service",
    "ai-service",
    "notification-service",
    "faq-service",
    "reporting-service",
    "tenant-service",
    "order-sync-service",
    "mcp-server"
  ]
}

variable "tags" {
  description = "Additional tags to apply to all resources"
  type        = map(string)
  default     = {}
}
