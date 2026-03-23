-- ============================================================
-- Migration: V4__create_ticket_activities_table (ticket-service)
-- Author: db-migration-agent
-- Date: 2026-03-23
-- Task: DB-002
-- Description: Create ticket_activities table for conversation history and audit trail
-- Rollback: DROP TABLE IF EXISTS ticket_activities;
-- ============================================================

CREATE TABLE IF NOT EXISTS ticket_activities (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ticket_id       UUID NOT NULL REFERENCES tickets(id),
    tenant_id       UUID NOT NULL,
    actor_id        UUID NOT NULL,
    actor_type      VARCHAR(20) NOT NULL,
    activity_type   VARCHAR(30) NOT NULL,
    content         TEXT,
    is_internal     BOOLEAN NOT NULL DEFAULT FALSE,
    attachments     JSONB,
    metadata        JSONB,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Indexes
CREATE INDEX IF NOT EXISTS idx_ticket_activities_ticket_id ON ticket_activities(ticket_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_ticket_activities_tenant_id ON ticket_activities(tenant_id);
CREATE INDEX IF NOT EXISTS idx_ticket_activities_actor ON ticket_activities(tenant_id, actor_id);
CREATE INDEX IF NOT EXISTS idx_ticket_activities_type ON ticket_activities(tenant_id, activity_type);

-- Row-Level Security
ALTER TABLE ticket_activities ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON ticket_activities
    AS PERMISSIVE FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid);

COMMENT ON TABLE ticket_activities IS 'Full audit trail and conversation history for each ticket. Append-only (no updates).';
COMMENT ON COLUMN ticket_activities.activity_type IS 'CUSTOMER_COMMENT | AGENT_COMMENT | AGENT_NOTE | STATUS_CHANGE | ASSIGNMENT_CHANGE | RESOLUTION | SYSTEM';
COMMENT ON COLUMN ticket_activities.actor_type IS 'CUSTOMER | AGENT | SYSTEM | AI_BOT';
COMMENT ON COLUMN ticket_activities.is_internal IS 'TRUE = internal note visible only to agents. FALSE = visible to customer.';
COMMENT ON COLUMN ticket_activities.attachments IS 'JSON array of attachment objects: [{ file_name, s3_key, mime_type, size_bytes }]';
COMMENT ON COLUMN ticket_activities.metadata IS 'Activity-type-specific data (e.g., old/new status for STATUS_CHANGE events)';
