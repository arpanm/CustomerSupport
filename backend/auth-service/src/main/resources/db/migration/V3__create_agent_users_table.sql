-- ============================================================
-- Migration: V3__create_agent_users_table
-- Author: db-migration-agent
-- Date: 2026-03-23
-- Task: DB-001
-- Description: Create agent_users table for support staff
-- Rollback: DROP TABLE IF EXISTS agent_users;
-- ============================================================

CREATE TABLE IF NOT EXISTS agent_users (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id             UUID NOT NULL,
    email                 VARCHAR(255) NOT NULL,
    display_name          VARCHAR(100) NOT NULL,
    password_hash         VARCHAR(255) NOT NULL,
    role                  VARCHAR(20) NOT NULL DEFAULT 'AGENT',
    team_id               UUID,
    is_active             BOOLEAN NOT NULL DEFAULT TRUE,
    is_available          BOOLEAN NOT NULL DEFAULT FALSE,
    two_fa_enabled        BOOLEAN NOT NULL DEFAULT FALSE,
    two_fa_secret         VARCHAR(64),
    last_login_at         TIMESTAMPTZ,
    password_changed_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(tenant_id, email)
);

-- Indexes
CREATE INDEX IF NOT EXISTS idx_agent_users_tenant_id ON agent_users(tenant_id);
CREATE INDEX IF NOT EXISTS idx_agent_users_email ON agent_users(tenant_id, email);
CREATE INDEX IF NOT EXISTS idx_agent_users_team_id ON agent_users(team_id) WHERE team_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_agent_users_role ON agent_users(tenant_id, role);

-- Row-Level Security
ALTER TABLE agent_users ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON agent_users
    AS PERMISSIVE FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid);

COMMENT ON TABLE agent_users IS 'Support staff who can access the agent dashboard. Roles: AGENT, TEAM_LEAD, ADMIN, SUPER_ADMIN.';
COMMENT ON COLUMN agent_users.role IS 'AGENT | TEAM_LEAD | ADMIN | SUPER_ADMIN';
COMMENT ON COLUMN agent_users.password_hash IS 'BCrypt hash of password (strength 12)';
COMMENT ON COLUMN agent_users.two_fa_secret IS 'TOTP secret for 2FA (Base32 encoded). NULL if 2FA not enrolled.';
