output "replication_group_id" {
  description = "ID of the ElastiCache replication group"
  value       = aws_elasticache_replication_group.supporthub.id
}

output "replication_group_arn" {
  description = "ARN of the ElastiCache replication group"
  value       = aws_elasticache_replication_group.supporthub.arn
}

output "primary_endpoint_address" {
  description = "Address of the primary endpoint for the replication group"
  value       = aws_elasticache_replication_group.supporthub.primary_endpoint_address
}

output "reader_endpoint_address" {
  description = "Address of the reader endpoint for the replication group"
  value       = aws_elasticache_replication_group.supporthub.reader_endpoint_address
}

output "port" {
  description = "Port number of the Redis cluster"
  value       = aws_elasticache_replication_group.supporthub.port
}

output "security_group_id" {
  description = "Security group ID attached to the Redis cluster"
  value       = aws_security_group.redis.id
}
