-- ============================================================
-- V1: Create oms_configs table with RLS tenant isolation
-- Service: order-sync-service
-- ============================================================

CREATE TABLE IF NOT EXISTS oms_configs (
    id               UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id        UUID          NOT NULL UNIQUE,
    oms_base_url     VARCHAR(500)  NOT NULL,
    api_key_encrypted BYTEA,
    auth_type        VARCHAR(20)   NOT NULL DEFAULT 'BEARER',
    header_name      VARCHAR(100)  DEFAULT 'Authorization',
    is_active        BOOLEAN       NOT NULL DEFAULT true,
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

-- Enable Row-Level Security for tenant isolation
ALTER TABLE oms_configs ENABLE ROW LEVEL SECURITY;

-- RLS policy: a session may only see/modify rows whose tenant_id matches
-- the session variable set by TenantContextFilter
CREATE POLICY tenant_isolation ON oms_configs
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Index for fast tenant-scoped lookups
CREATE INDEX IF NOT EXISTS idx_oms_configs_tenant ON oms_configs (tenant_id);
