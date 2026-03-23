###############################################################################
# SupportHub – MSK (Managed Kafka) Module
# Creates: MSK cluster, MSK configuration, security group
###############################################################################

locals {
  identifier = "${var.project}-${var.environment}"
  tags = merge(var.tags, {
    Environment = var.environment
    Project     = var.project
    ManagedBy   = "terraform"
  })
}

###############################################################################
# Security Group
###############################################################################

resource "aws_security_group" "msk" {
  name        = "${local.identifier}-msk-sg"
  description = "Security group for MSK Kafka – allow ports 9092/9094 from EKS nodes only"
  vpc_id      = var.vpc_id

  ingress {
    description     = "Kafka plaintext from EKS nodes"
    from_port       = 9092
    to_port         = 9092
    protocol        = "tcp"
    security_groups = [var.eks_security_group_id]
  }

  ingress {
    description     = "Kafka TLS from EKS nodes"
    from_port       = 9094
    to_port         = 9094
    protocol        = "tcp"
    security_groups = [var.eks_security_group_id]
  }

  ingress {
    description     = "Kafka IAM/SASL from EKS nodes"
    from_port       = 9098
    to_port         = 9098
    protocol        = "tcp"
    security_groups = [var.eks_security_group_id]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
    description = "Allow all outbound"
  }

  tags = merge(local.tags, { Name = "${local.identifier}-msk-sg" })
}

###############################################################################
# MSK Configuration
###############################################################################

resource "aws_msk_configuration" "supporthub" {
  name           = "${local.identifier}-msk-config"
  kafka_versions = [var.kafka_version]
  description    = "SupportHub MSK cluster configuration – ${var.environment}"

  server_properties = <<-PROPERTIES
    auto.create.topics.enable=false
    default.replication.factor=3
    min.insync.replicas=2
    num.io.threads=8
    num.network.threads=5
    num.partitions=3
    num.replica.fetchers=2
    replica.lag.time.max.ms=30000
    socket.receive.buffer.bytes=102400
    socket.request.max.bytes=104857600
    socket.send.buffer.bytes=102400
    unclean.leader.election.enable=false
    zookeeper.session.timeout.ms=18000
    log.retention.hours=168
    log.segment.bytes=1073741824
    log.retention.check.interval.ms=300000
  PROPERTIES
}

###############################################################################
# MSK Cluster
###############################################################################

resource "aws_msk_cluster" "supporthub" {
  cluster_name           = local.identifier
  kafka_version          = var.kafka_version
  number_of_broker_nodes = var.number_of_broker_nodes
  configuration_info {
    arn      = aws_msk_configuration.supporthub.arn
    revision = aws_msk_configuration.supporthub.latest_revision
  }

  broker_node_group_info {
    instance_type  = var.instance_type
    client_subnets = var.subnet_ids
    storage_info {
      ebs_storage_info {
        volume_size = 50
      }
    }
    security_groups = [aws_security_group.msk.id]
  }

  encryption_info {
    encryption_in_transit {
      client_broker = "TLS"
      in_cluster    = true
    }
  }

  client_authentication {
    sasl {
      iam = true
    }
  }

  open_monitoring {
    prometheus {
      jmx_exporter {
        enabled_in_broker = true
      }
      node_exporter {
        enabled_in_broker = true
      }
    }
  }

  logging_info {
    broker_logs {
      cloudwatch_logs {
        enabled   = true
        log_group = "/aws/msk/${local.identifier}"
      }
    }
  }

  tags = local.tags
}
