-- ============================================================
-- Migration: V4__create_teams_table
-- Author: db-migration-agent
-- Date: 2026-03-23
-- Task: DB-001
-- Description: Create teams table and add FK from agent_users
-- Rollback: ALTER TABLE agent_users DROP CONSTRAINT IF EXISTS fk_agent_users_team_id;
--           DROP TABLE IF EXISTS teams;
-- ============================================================

CREATE TABLE IF NOT EXISTS teams (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id         UUID NOT NULL REFERENCES tenants(id),
    name              VARCHAR(100) NOT NULL,
    slug              VARCHAR(100) NOT NULL,
    description       TEXT,
    lead_agent_id     UUID REFERENCES agent_users(id),
    is_active         BOOLEAN NOT NULL DEFAULT TRUE,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(tenant_id, slug)
);

-- Add FK from agent_users to teams after teams table exists
ALTER TABLE agent_users ADD CONSTRAINT fk_agent_users_team_id
    FOREIGN KEY (team_id) REFERENCES teams(id);

-- Indexes
CREATE INDEX IF NOT EXISTS idx_teams_tenant_id ON teams(tenant_id);
CREATE INDEX IF NOT EXISTS idx_teams_lead_agent_id ON teams(lead_agent_id) WHERE lead_agent_id IS NOT NULL;

-- Row-Level Security
ALTER TABLE teams ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON teams
    AS PERMISSIVE FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid);

COMMENT ON TABLE teams IS 'Support teams grouping agents. Each team has an optional lead agent.';
COMMENT ON COLUMN teams.slug IS 'URL-safe team identifier unique within tenant';
