-- ============================================================
-- Migration: V6__create_sla_policies_table (ticket-service)
-- Author: db-migration-agent
-- Date: 2026-03-23
-- Task: DB-002
-- Description: Create sla_policies table for configurable SLA thresholds per category/priority
-- Rollback: DROP TABLE IF EXISTS sla_policies;
-- ============================================================

CREATE TABLE IF NOT EXISTS sla_policies (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id             UUID NOT NULL,
    name                  VARCHAR(100) NOT NULL,
    category_id           UUID REFERENCES ticket_categories(id),
    priority              VARCHAR(20),
    first_response_hours  INTEGER NOT NULL DEFAULT 4,
    resolution_hours      INTEGER NOT NULL DEFAULT 24,
    is_active             BOOLEAN NOT NULL DEFAULT TRUE,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Indexes
CREATE INDEX IF NOT EXISTS idx_sla_policies_tenant_id ON sla_policies(tenant_id);
CREATE INDEX IF NOT EXISTS idx_sla_policies_category ON sla_policies(tenant_id, category_id);
CREATE INDEX IF NOT EXISTS idx_sla_policies_priority ON sla_policies(tenant_id, priority);
CREATE INDEX IF NOT EXISTS idx_sla_policies_active ON sla_policies(tenant_id, is_active);

-- Row-Level Security
ALTER TABLE sla_policies ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON sla_policies
    AS PERMISSIVE FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid);

COMMENT ON TABLE sla_policies IS 'SLA policy rules. Most specific match wins: category+priority > category-only > priority-only > default.';
COMMENT ON COLUMN sla_policies.category_id IS 'NULL = applies to all categories for this priority';
COMMENT ON COLUMN sla_policies.priority IS 'NULL = applies to all priorities for this category. LOW | MEDIUM | HIGH | URGENT';
COMMENT ON COLUMN sla_policies.first_response_hours IS 'Hours within which agent must first respond';
COMMENT ON COLUMN sla_policies.resolution_hours IS 'Hours within which ticket must be resolved';
