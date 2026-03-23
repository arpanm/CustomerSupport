variable "project" {
  description = "Project name used for resource naming and tagging"
  type        = string
  default     = "supporthub"
}

variable "environment" {
  description = "Deployment environment (dev, staging, prod)"
  type        = string
}

variable "bucket_suffix" {
  description = "Unique suffix appended to the bucket name to ensure global uniqueness"
  type        = string
}

variable "kms_key_id" {
  description = "KMS key ID for SSE-KMS encryption. If null, AES256 is used."
  type        = string
  default     = null
}

variable "cors_allowed_origins" {
  description = "List of allowed origins for CORS configuration (for frontend direct uploads)"
  type        = list(string)
  default     = ["https://supporthub.in"]
}

variable "tags" {
  description = "Additional tags to apply to all resources"
  type        = map(string)
  default     = {}
}
