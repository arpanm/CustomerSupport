-- ============================================================
-- SupportHub PostgreSQL Initialization Script
-- Task: INFRA-001
-- Runs automatically on first container start via
-- /docker-entrypoint-initdb.d/
--
-- Installs required extensions and creates the test database.
-- The main database (supporthub) is created by the POSTGRES_DB
-- environment variable before this script runs.
-- ============================================================

-- Enable pgvector for AI embedding storage (used by ai-service, faq-service)
CREATE EXTENSION IF NOT EXISTS vector;

-- Enable uuid-ossp for UUID generation functions
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Create the test database used by Testcontainers-based integration tests
-- that run against the local dev stack instead of a fresh container.
CREATE DATABASE supporthub_test;

-- Grant the application user access to the test DB
GRANT ALL PRIVILEGES ON DATABASE supporthub_test TO supporthub;

-- Connect to the test database and enable extensions there too
\connect supporthub_test

CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
