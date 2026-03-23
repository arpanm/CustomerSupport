output "bucket_id" {
  description = "Name (ID) of the S3 attachments bucket"
  value       = aws_s3_bucket.supporthub_attachments.id
}

output "bucket_arn" {
  description = "ARN of the S3 attachments bucket"
  value       = aws_s3_bucket.supporthub_attachments.arn
}

output "bucket_domain_name" {
  description = "Bucket domain name (used for direct upload URLs)"
  value       = aws_s3_bucket.supporthub_attachments.bucket_domain_name
}

output "bucket_regional_domain_name" {
  description = "Regional bucket domain name"
  value       = aws_s3_bucket.supporthub_attachments.bucket_regional_domain_name
}
