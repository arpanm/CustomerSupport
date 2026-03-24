-- V9__create_attachments_table.sql
-- Creates the attachments table for storing file upload metadata.
-- Files are stored in MinIO; this table tracks attachment lifecycle state.

CREATE TABLE IF NOT EXISTS attachments (
    id               UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id        UUID        NOT NULL,
    ticket_id        UUID,                          -- NULL until ticket is created
    file_name        VARCHAR(500) NOT NULL,
    content_type     VARCHAR(200) NOT NULL,
    minio_object_key VARCHAR(1000) NOT NULL,
    file_size_bytes  BIGINT,
    status           VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT attachments_status_check
        CHECK (status IN ('PENDING', 'LINKED'))
);

-- Tenant isolation index (used by all tenant-scoped queries)
CREATE INDEX IF NOT EXISTS idx_attachments_tenant_id
    ON attachments (tenant_id);

-- Ticket lookup index (used when fetching attachments for a ticket)
CREATE INDEX IF NOT EXISTS idx_attachments_ticket_id
    ON attachments (ticket_id)
    WHERE ticket_id IS NOT NULL;

-- Combined tenant + ticket index (most common access pattern)
CREATE INDEX IF NOT EXISTS idx_attachments_tenant_ticket
    ON attachments (tenant_id, ticket_id)
    WHERE ticket_id IS NOT NULL;

-- Enable Row-Level Security
ALTER TABLE attachments ENABLE ROW LEVEL SECURITY;

-- RLS policy: each session can only see rows for its own tenant
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_policies
        WHERE tablename = 'attachments' AND policyname = 'attachments_tenant_isolation'
    ) THEN
        CREATE POLICY attachments_tenant_isolation ON attachments
            USING (tenant_id::text = current_setting('app.current_tenant', true));
    END IF;
END
$$;
