-- ============================================================
-- Migration: V8__create_faq_entries_table (ticket-service)
-- Author: db-migration-agent
-- Date: 2026-03-23
-- Task: DB-002
-- Description: Create faq_entries table with pgvector support for semantic search
-- Rollback: DROP TABLE IF EXISTS faq_entries;
--           DROP EXTENSION IF EXISTS vector;
-- Note: Requires pgvector extension. Enabled via CREATE EXTENSION IF NOT EXISTS vector.
-- ============================================================

-- Enable pgvector extension (idempotent, requires pg_vector to be installed in Docker)
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS faq_entries (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL,
    title               VARCHAR(500) NOT NULL,
    content             TEXT NOT NULL,
    category_tags       TEXT[],
    is_published        BOOLEAN NOT NULL DEFAULT FALSE,
    embedding           VECTOR(1536),
    cms_external_id     VARCHAR(100),
    view_count          INTEGER NOT NULL DEFAULT 0,
    helpful_count       INTEGER NOT NULL DEFAULT 0,
    not_helpful_count   INTEGER NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- HNSW index for ANN search (fast cosine similarity)
-- m=16: max connections per node. ef_construction=64: build-time accuracy trade-off.
CREATE INDEX IF NOT EXISTS idx_faq_entries_embedding
    ON faq_entries USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);

-- Standard indexes
CREATE INDEX IF NOT EXISTS idx_faq_entries_tenant ON faq_entries(tenant_id);
CREATE INDEX IF NOT EXISTS idx_faq_entries_published ON faq_entries(tenant_id, is_published);
CREATE INDEX IF NOT EXISTS idx_faq_entries_cms_id ON faq_entries(tenant_id, cms_external_id) WHERE cms_external_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_faq_entries_helpful ON faq_entries(tenant_id, helpful_count DESC) WHERE is_published = TRUE;

-- Row-Level Security
ALTER TABLE faq_entries ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON faq_entries
    AS PERMISSIVE FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid);

COMMENT ON TABLE faq_entries IS 'FAQ knowledge base entries synced from Strapi CMS. Supports semantic search via pgvector.';
COMMENT ON COLUMN faq_entries.embedding IS 'Anthropic/OpenAI text-embedding-3-small vector (1536 dimensions). NULL until embedding job runs.';
COMMENT ON COLUMN faq_entries.cms_external_id IS 'External ID from Strapi CMS for sync reconciliation';
COMMENT ON COLUMN faq_entries.category_tags IS 'Free-form category tags for filtering (e.g., ["order", "refund"])';
COMMENT ON COLUMN faq_entries.helpful_count IS 'Number of times customers marked this FAQ as helpful';
COMMENT ON COLUMN faq_entries.not_helpful_count IS 'Number of times customers marked this FAQ as not helpful';
