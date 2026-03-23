###############################################################################
# Production Environment Outputs
###############################################################################

output "eks_cluster_name" {
  description = "EKS cluster name"
  value       = module.eks.cluster_name
}

output "eks_cluster_endpoint" {
  description = "EKS cluster API server endpoint"
  value       = module.eks.cluster_endpoint
}

output "eks_oidc_provider_arn" {
  description = "OIDC provider ARN for IRSA"
  value       = module.eks.oidc_provider_arn
}

output "rds_endpoint" {
  description = "RDS PostgreSQL endpoint"
  value       = module.rds.db_endpoint
  sensitive   = true
}

output "redis_primary_endpoint" {
  description = "ElastiCache Redis primary endpoint"
  value       = module.elasticache.primary_endpoint_address
  sensitive   = true
}

output "msk_bootstrap_brokers_tls" {
  description = "MSK Kafka TLS bootstrap brokers"
  value       = module.msk.bootstrap_brokers_tls
  sensitive   = true
}

output "s3_attachments_bucket" {
  description = "S3 attachments bucket name"
  value       = module.s3.bucket_id
}

output "ecr_repository_urls" {
  description = "ECR repository URLs per service"
  value       = module.ecr.repository_urls
}

output "opensearch_endpoint" {
  description = "OpenSearch domain endpoint"
  value       = module.opensearch.endpoint
  sensitive   = true
}
