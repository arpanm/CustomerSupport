-- ============================================================
-- Migration: V1__create_tenants_table
-- Author: db-migration-agent
-- Date: 2026-03-23
-- Task: DB-001
-- Description: Create tenants table for multi-tenant platform
-- Rollback: DROP TABLE IF EXISTS tenants;
-- ============================================================

CREATE TABLE IF NOT EXISTS tenants (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    slug              VARCHAR(100) NOT NULL UNIQUE,
    name              VARCHAR(200) NOT NULL,
    domain            VARCHAR(255),
    plan              VARCHAR(20) NOT NULL DEFAULT 'starter',
    timezone          VARCHAR(50) NOT NULL DEFAULT 'Asia/Kolkata',
    locale            VARCHAR(10) NOT NULL DEFAULT 'en-IN',
    branding          JSONB NOT NULL DEFAULT '{}',
    ticket_prefix     VARCHAR(10) NOT NULL DEFAULT 'TKT',
    is_active         BOOLEAN NOT NULL DEFAULT TRUE,
    max_agents        INTEGER NOT NULL DEFAULT 10,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Indexes
CREATE INDEX IF NOT EXISTS idx_tenants_slug ON tenants(slug);
CREATE INDEX IF NOT EXISTS idx_tenants_domain ON tenants(domain) WHERE domain IS NOT NULL;

-- No RLS on tenants table (global table, tenant_id is self-referential)
-- Tenant table itself is protected by service-level checks

COMMENT ON TABLE tenants IS 'Multi-tenant platform tenants. Each represents a store/brand (e.g., FoodCo).';
COMMENT ON COLUMN tenants.slug IS 'URL-safe identifier, used as subdomain: {slug}.supporthub.in';
COMMENT ON COLUMN tenants.branding IS 'JSON: { logo_url, primary_color, secondary_color, support_email, support_phone }';
COMMENT ON COLUMN tenants.ticket_prefix IS 'Prefix for ticket numbers, e.g., FC for FoodCo => FC-2024-001234';
COMMENT ON COLUMN tenants.plan IS 'starter | growth | enterprise';
