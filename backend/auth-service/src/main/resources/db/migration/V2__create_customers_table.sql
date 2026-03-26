-- ============================================================
-- Migration: V2__create_customers_table
-- Author: db-migration-agent
-- Date: 2026-03-23
-- Task: DB-001
-- Description: Create customers table with encrypted PII fields
-- Rollback: DROP TABLE IF EXISTS customers;
-- ============================================================

CREATE TABLE IF NOT EXISTS customers (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id             UUID NOT NULL,
    phone_encrypted       BYTEA NOT NULL,
    phone_hash            VARCHAR(64) NOT NULL,
    email_encrypted       BYTEA,
    email_hash            VARCHAR(64),
    name_encrypted        BYTEA,
    preferred_language    VARCHAR(10) NOT NULL DEFAULT 'en',
    is_active             BOOLEAN NOT NULL DEFAULT TRUE,
    last_login_at         TIMESTAMPTZ,
    total_tickets         INTEGER NOT NULL DEFAULT 0,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(tenant_id, phone_hash)
);

-- Indexes
CREATE INDEX IF NOT EXISTS idx_customers_tenant_id ON customers(tenant_id);
CREATE INDEX IF NOT EXISTS idx_customers_phone_hash ON customers(tenant_id, phone_hash);
CREATE INDEX IF NOT EXISTS idx_customers_email_hash ON customers(tenant_id, email_hash) WHERE email_hash IS NOT NULL;

-- Row-Level Security
ALTER TABLE customers ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON customers
    AS PERMISSIVE FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid);

COMMENT ON TABLE customers IS 'End-user customers. PII fields (phone, email, name) are AES-256-GCM encrypted at rest.';
COMMENT ON COLUMN customers.phone_encrypted IS 'AES-256-GCM encrypted phone number bytes';
COMMENT ON COLUMN customers.phone_hash IS 'HMAC-SHA256 of phone for lookup without decryption';
COMMENT ON COLUMN customers.email_hash IS 'HMAC-SHA256 of email for lookup. NULL if no email.';
