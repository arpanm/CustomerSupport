-- ============================================================
-- Migration: V7__create_resolution_templates_table (ticket-service)
-- Author: db-migration-agent
-- Date: 2026-03-23
-- Task: DB-002
-- Description: Create resolution_templates table for canned responses and resolution helpers
-- Rollback: DROP TABLE IF EXISTS resolution_templates;
-- ============================================================

CREATE TABLE IF NOT EXISTS resolution_templates (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL,
    title           VARCHAR(200) NOT NULL,
    content         TEXT NOT NULL,
    category_id     UUID REFERENCES ticket_categories(id),
    tags            TEXT[],
    use_count       INTEGER NOT NULL DEFAULT 0,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_by      UUID,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Indexes
CREATE INDEX IF NOT EXISTS idx_resolution_templates_tenant ON resolution_templates(tenant_id);
CREATE INDEX IF NOT EXISTS idx_resolution_templates_category ON resolution_templates(tenant_id, category_id);
CREATE INDEX IF NOT EXISTS idx_resolution_templates_active ON resolution_templates(tenant_id, is_active);
CREATE INDEX IF NOT EXISTS idx_resolution_templates_use_count ON resolution_templates(tenant_id, use_count DESC);

-- Row-Level Security
ALTER TABLE resolution_templates ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON resolution_templates
    AS PERMISSIVE FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid);

COMMENT ON TABLE resolution_templates IS 'Canned responses and resolution templates for agent efficiency. AI may suggest these.';
COMMENT ON COLUMN resolution_templates.content IS 'Template body. May contain placeholders: {{customer_name}}, {{ticket_number}}, {{order_id}}';
COMMENT ON COLUMN resolution_templates.use_count IS 'Incremented each time this template is applied to a ticket activity';
COMMENT ON COLUMN resolution_templates.created_by IS 'UUID of the agent_user who created this template. NULL for system-seeded templates.';
