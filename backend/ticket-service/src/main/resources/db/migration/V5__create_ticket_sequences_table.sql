-- ============================================================
-- Migration: V5__create_ticket_sequences_table (ticket-service)
-- Author: db-migration-agent
-- Date: 2026-03-23
-- Task: DB-002
-- Description: Create ticket_sequences table for fallback ticket number generation
-- Rollback: DROP TABLE IF EXISTS ticket_sequences;
-- ============================================================

CREATE TABLE IF NOT EXISTS ticket_sequences (
    tenant_id       UUID NOT NULL,
    year            INTEGER NOT NULL,
    current_seq     INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (tenant_id, year)
);

COMMENT ON TABLE ticket_sequences IS 'Per-tenant per-year ticket sequence counter. Used as fallback if Redis INCR is unavailable.';
COMMENT ON COLUMN ticket_sequences.current_seq IS 'Monotonically increasing counter. Increment using UPDATE ... RETURNING to avoid races.';
COMMENT ON COLUMN ticket_sequences.year IS 'Calendar year (e.g., 2024). New row created each year per tenant.';
