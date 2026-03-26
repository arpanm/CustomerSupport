-- ============================================================
-- Migration: V5__create_refresh_tokens_table
-- Author: db-migration-agent
-- Date: 2026-03-23
-- Task: DB-001
-- Description: Create refresh_tokens table for JWT refresh token storage
-- Rollback: DROP TABLE IF EXISTS refresh_tokens;
-- ============================================================

CREATE TABLE IF NOT EXISTS refresh_tokens (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id         UUID NOT NULL,
    token_hash        VARCHAR(64) NOT NULL UNIQUE,
    subject_id        UUID NOT NULL,
    subject_type      VARCHAR(20) NOT NULL,
    expires_at        TIMESTAMPTZ NOT NULL,
    revoked_at        TIMESTAMPTZ,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Indexes
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_token_hash ON refresh_tokens(token_hash);
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_subject ON refresh_tokens(tenant_id, subject_id, subject_type);
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_expires ON refresh_tokens(expires_at);

-- Row-Level Security
ALTER TABLE refresh_tokens ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON refresh_tokens
    AS PERMISSIVE FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid);

COMMENT ON TABLE refresh_tokens IS 'Persistent refresh tokens. Stored as SHA-256 hash, actual token in httpOnly cookie.';
COMMENT ON COLUMN refresh_tokens.subject_type IS 'CUSTOMER or AGENT — which user type this token belongs to';
COMMENT ON COLUMN refresh_tokens.token_hash IS 'SHA-256 of the raw refresh token (hex-encoded)';
