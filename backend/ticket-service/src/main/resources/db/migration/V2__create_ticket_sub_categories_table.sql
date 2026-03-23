-- ============================================================
-- Migration: V2__create_ticket_sub_categories_table (ticket-service)
-- Author: db-migration-agent
-- Date: 2026-03-23
-- Task: DB-002
-- Description: Create ticket_sub_categories table for granular ticket classification
-- Rollback: DROP TABLE IF EXISTS ticket_sub_categories;
-- ============================================================

CREATE TABLE IF NOT EXISTS ticket_sub_categories (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL,
    category_id     UUID NOT NULL REFERENCES ticket_categories(id),
    name            VARCHAR(100) NOT NULL,
    slug            VARCHAR(100) NOT NULL,
    sort_order      INTEGER NOT NULL DEFAULT 0,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(tenant_id, category_id, slug)
);

-- Indexes
CREATE INDEX IF NOT EXISTS idx_ticket_sub_cats_tenant_id ON ticket_sub_categories(tenant_id);
CREATE INDEX IF NOT EXISTS idx_ticket_sub_cats_category_id ON ticket_sub_categories(category_id);
CREATE INDEX IF NOT EXISTS idx_ticket_sub_cats_active ON ticket_sub_categories(tenant_id, is_active);

-- Row-Level Security
ALTER TABLE ticket_sub_categories ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON ticket_sub_categories
    AS PERMISSIVE FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid);

COMMENT ON TABLE ticket_sub_categories IS 'Sub-categories under each ticket category (e.g., Order Issue > Wrong Item, Order Issue > Missing Item).';
COMMENT ON COLUMN ticket_sub_categories.slug IS 'URL-safe identifier, unique per tenant+category combination';
