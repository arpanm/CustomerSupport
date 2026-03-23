output "db_instance_id" {
  description = "RDS instance identifier"
  value       = aws_db_instance.supporthub.id
}

output "db_instance_arn" {
  description = "ARN of the RDS instance"
  value       = aws_db_instance.supporthub.arn
}

output "db_endpoint" {
  description = "Connection endpoint of the RDS instance (host:port)"
  value       = aws_db_instance.supporthub.endpoint
}

output "db_address" {
  description = "Hostname of the RDS instance"
  value       = aws_db_instance.supporthub.address
}

output "db_port" {
  description = "Port of the RDS instance"
  value       = aws_db_instance.supporthub.port
}

output "db_name" {
  description = "Name of the database"
  value       = aws_db_instance.supporthub.db_name
}

output "security_group_id" {
  description = "Security group ID attached to the RDS instance"
  value       = aws_security_group.rds.id
}
