-- V1__create_tenants_table.sql
-- Creates the core tenants table with RLS isolation policy.

CREATE TABLE IF NOT EXISTS tenants (
  id         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id  UUID         NOT NULL,                          -- self-reference (same as id for tenant-service RLS compat)
  slug       VARCHAR(100) NOT NULL UNIQUE,
  name       VARCHAR(255) NOT NULL,
  plan_type  VARCHAR(50)  NOT NULL DEFAULT 'FREE',
  status     VARCHAR(50)  NOT NULL DEFAULT 'PENDING',
  timezone   VARCHAR(100) NOT NULL DEFAULT 'Asia/Kolkata',
  locale     VARCHAR(20)  NOT NULL DEFAULT 'en-IN',
  created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_tenants_slug ON tenants(slug);

ALTER TABLE tenants ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON tenants
  USING (
    tenant_id = current_setting('app.tenant_id', true)::uuid
    OR current_setting('app.tenant_id', true) = ''
  );
