###############################################################################
# SupportHub – S3 Module
# Creates: S3 bucket for attachments with versioning, encryption, lifecycle,
#          public access block, and CORS configuration
###############################################################################

locals {
  bucket_name = "${var.project}-${var.environment}-attachments-${var.bucket_suffix}"
  tags = merge(var.tags, {
    Environment = var.environment
    Project     = var.project
    ManagedBy   = "terraform"
  })
}

###############################################################################
# S3 Bucket
###############################################################################

resource "aws_s3_bucket" "supporthub_attachments" {
  bucket        = local.bucket_name
  force_destroy = false

  tags = local.tags
}

###############################################################################
# Versioning
###############################################################################

resource "aws_s3_bucket_versioning" "supporthub_attachments" {
  bucket = aws_s3_bucket.supporthub_attachments.id

  versioning_configuration {
    status = "Enabled"
  }
}

###############################################################################
# Server-Side Encryption
###############################################################################

resource "aws_s3_bucket_server_side_encryption_configuration" "supporthub_attachments" {
  bucket = aws_s3_bucket.supporthub_attachments.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm     = var.kms_key_id != null ? "aws:kms" : "AES256"
      kms_master_key_id = var.kms_key_id
    }
    bucket_key_enabled = var.kms_key_id != null ? true : false
  }
}

###############################################################################
# Lifecycle Configuration
###############################################################################

resource "aws_s3_bucket_lifecycle_configuration" "supporthub_attachments" {
  bucket = aws_s3_bucket.supporthub_attachments.id

  rule {
    id     = "expire-noncurrent-versions"
    status = "Enabled"

    noncurrent_version_expiration {
      noncurrent_days = 30
    }
  }

  rule {
    id     = "abort-incomplete-multipart"
    status = "Enabled"

    abort_incomplete_multipart_upload {
      days_after_initiation = 7
    }
  }

  depends_on = [aws_s3_bucket_versioning.supporthub_attachments]
}

###############################################################################
# Public Access Block
###############################################################################

resource "aws_s3_bucket_public_access_block" "supporthub_attachments" {
  bucket = aws_s3_bucket.supporthub_attachments.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

###############################################################################
# CORS Configuration (for frontend direct uploads)
###############################################################################

resource "aws_s3_bucket_cors_configuration" "supporthub_attachments" {
  bucket = aws_s3_bucket.supporthub_attachments.id

  cors_rule {
    allowed_headers = ["*"]
    allowed_methods = ["PUT"]
    allowed_origins = var.cors_allowed_origins
    expose_headers  = ["ETag"]
    max_age_seconds = 3000
  }
}
