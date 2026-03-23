-- ============================================================
-- V1: Enable pgvector extension and create faq_entries table
-- Service: faq-service
-- ============================================================

-- Enable pgvector extension (idempotent)
CREATE EXTENSION IF NOT EXISTS vector;

-- ============================================================
-- faq_entries
-- ============================================================
-- Stores FAQ content managed either manually via the admin API
-- or synced from Strapi CMS (identified by strapi_id).
-- The embedding column holds a 1536-dimensional float vector
-- produced by OpenAI text-embedding-3-small for cosine
-- similarity search via pgvector.
-- ============================================================
CREATE TABLE IF NOT EXISTS faq_entries (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id    UUID         NOT NULL,
    category_id  UUID,
    question     TEXT         NOT NULL,
    answer       TEXT         NOT NULL,
    tags         TEXT[]       NOT NULL DEFAULT '{}',
    strapi_id    VARCHAR(100),
    is_published BOOLEAN      NOT NULL DEFAULT FALSE,
    view_count   BIGINT       NOT NULL DEFAULT 0,
    embedding    VECTOR(1536),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- Row-Level Security — tenant isolation
ALTER TABLE faq_entries ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON faq_entries
    USING (tenant_id = current_setting('app.current_tenant', TRUE)::uuid);

-- ============================================================
-- Indexes
-- ============================================================

-- Primary tenant lookup
CREATE INDEX IF NOT EXISTS idx_faq_entries_tenant
    ON faq_entries (tenant_id);

-- Tenant + category filter
CREATE INDEX IF NOT EXISTS idx_faq_entries_tenant_category
    ON faq_entries (tenant_id, category_id);

-- Strapi ID lookup for webhook upsert
CREATE UNIQUE INDEX IF NOT EXISTS idx_faq_entries_strapi_id
    ON faq_entries (tenant_id, strapi_id)
    WHERE strapi_id IS NOT NULL;

-- Published status filter
CREATE INDEX IF NOT EXISTS idx_faq_entries_published
    ON faq_entries (tenant_id, is_published);

-- pgvector HNSW index for approximate nearest-neighbour cosine search
-- m=16 and ef_construction=64 are sensible defaults for up to ~1M vectors
CREATE INDEX IF NOT EXISTS idx_faq_hnsw
    ON faq_entries USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);
