-- ============================================================
-- Migration: V3__create_tickets_table (ticket-service)
-- Author: db-migration-agent
-- Date: 2026-03-23
-- Task: DB-002
-- Description: Create core tickets table — the central entity of the platform
-- Rollback: DROP TABLE IF EXISTS tickets;
-- ============================================================

CREATE TABLE IF NOT EXISTS tickets (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ticket_number               VARCHAR(30) NOT NULL,
    tenant_id                   UUID NOT NULL,
    customer_id                 UUID NOT NULL,
    order_id                    UUID,
    title                       VARCHAR(200) NOT NULL,
    description                 TEXT NOT NULL,
    category_id                 UUID REFERENCES ticket_categories(id),
    sub_category_id             UUID REFERENCES ticket_sub_categories(id),
    ticket_type                 VARCHAR(20) NOT NULL DEFAULT 'COMPLAINT',
    priority                    VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',
    status                      VARCHAR(40) NOT NULL DEFAULT 'OPEN',
    channel                     VARCHAR(20) NOT NULL DEFAULT 'web',
    assigned_agent_id           UUID,
    assigned_team_id            UUID,
    custom_fields               JSONB,
    tags                        TEXT[],
    sla_first_response_due_at   TIMESTAMPTZ,
    sla_resolution_due_at       TIMESTAMPTZ,
    sla_first_response_breached BOOLEAN NOT NULL DEFAULT FALSE,
    sla_resolution_breached     BOOLEAN NOT NULL DEFAULT FALSE,
    sentiment_score             FLOAT,
    sentiment_label             VARCHAR(20),
    sentiment_updated_at        TIMESTAMPTZ,
    first_responded_at          TIMESTAMPTZ,
    resolved_at                 TIMESTAMPTZ,
    closed_at                   TIMESTAMPTZ,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(tenant_id, ticket_number)
);

-- Indexes
CREATE INDEX IF NOT EXISTS idx_tickets_tenant_id ON tickets(tenant_id);
CREATE INDEX IF NOT EXISTS idx_tickets_customer_id ON tickets(tenant_id, customer_id);
CREATE INDEX IF NOT EXISTS idx_tickets_status ON tickets(tenant_id, status);
CREATE INDEX IF NOT EXISTS idx_tickets_assigned_agent ON tickets(tenant_id, assigned_agent_id) WHERE assigned_agent_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_tickets_assigned_team ON tickets(tenant_id, assigned_team_id) WHERE assigned_team_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_tickets_category ON tickets(tenant_id, category_id);
CREATE INDEX IF NOT EXISTS idx_tickets_created_at ON tickets(tenant_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_tickets_sla_breach ON tickets(sla_resolution_due_at) WHERE status NOT IN ('RESOLVED', 'CLOSED');
CREATE INDEX IF NOT EXISTS idx_tickets_sentiment_label ON tickets(tenant_id, sentiment_label) WHERE sentiment_label IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_tickets_order_id ON tickets(tenant_id, order_id) WHERE order_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_tickets_priority ON tickets(tenant_id, priority);

-- Row-Level Security
ALTER TABLE tickets ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON tickets
    AS PERMISSIVE FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid);

COMMENT ON TABLE tickets IS 'Core support tickets. Central entity of the SupportHub platform.';
COMMENT ON COLUMN tickets.ticket_number IS 'Human-readable ID: {PREFIX}-{YEAR}-{SEQ} e.g. FC-2024-001234';
COMMENT ON COLUMN tickets.status IS 'OPEN | PENDING_AGENT_RESPONSE | PENDING_CUSTOMER_RESPONSE | IN_PROGRESS | ESCALATED | RESOLVED | CLOSED | REOPENED';
COMMENT ON COLUMN tickets.channel IS 'web | mobile | whatsapp | email | phone | mcp';
COMMENT ON COLUMN tickets.ticket_type IS 'COMPLAINT | INQUIRY | RETURN | REFUND | FEEDBACK | ESCALATION';
COMMENT ON COLUMN tickets.priority IS 'LOW | MEDIUM | HIGH | URGENT';
COMMENT ON COLUMN tickets.sentiment_score IS 'AI-computed sentiment: -1.0 (very_negative) to 1.0 (very_positive)';
COMMENT ON COLUMN tickets.sentiment_label IS 'very_negative | negative | neutral | positive | very_positive';
COMMENT ON COLUMN tickets.custom_fields IS 'Tenant-specific extra fields as key-value JSON object';
COMMENT ON COLUMN tickets.tags IS 'Free-form text tags for categorization and filtering';
