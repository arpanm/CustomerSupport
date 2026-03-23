output "cluster_arn" {
  description = "ARN of the MSK cluster"
  value       = aws_msk_cluster.supporthub.arn
}

output "cluster_name" {
  description = "Name of the MSK cluster"
  value       = aws_msk_cluster.supporthub.cluster_name
}

output "bootstrap_brokers_tls" {
  description = "TLS connection host:port pairs for the MSK cluster bootstrap brokers"
  value       = aws_msk_cluster.supporthub.bootstrap_brokers_tls
}

output "bootstrap_brokers_sasl_iam" {
  description = "SASL/IAM connection host:port pairs for the MSK cluster bootstrap brokers"
  value       = aws_msk_cluster.supporthub.bootstrap_brokers_sasl_iam
}

output "zookeeper_connect_string" {
  description = "Zookeeper connection string for the MSK cluster"
  value       = aws_msk_cluster.supporthub.zookeeper_connect_string
}

output "security_group_id" {
  description = "Security group ID attached to the MSK cluster"
  value       = aws_security_group.msk.id
}
