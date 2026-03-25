-- V2__create_tenant_configs_table.sql
-- Creates the tenant_configs key-value store with RLS isolation policy.

CREATE TABLE IF NOT EXISTS tenant_configs (
  id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id    UUID         NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
  config_key   VARCHAR(200) NOT NULL,
  config_value TEXT,
  created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  updated_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  UNIQUE(tenant_id, config_key)
);

CREATE INDEX IF NOT EXISTS idx_tenant_configs_tenant_id ON tenant_configs(tenant_id);

ALTER TABLE tenant_configs ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON tenant_configs
  USING (
    tenant_id = current_setting('app.tenant_id', true)::uuid
    OR current_setting('app.tenant_id', true) = ''
  );
