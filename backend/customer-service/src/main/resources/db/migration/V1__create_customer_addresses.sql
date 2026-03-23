-- =============================================================================
-- V1__create_customer_addresses.sql
-- Customer-service schema: customer_addresses table
--
-- NOTE: The `customers` table is owned by auth-service (auth-service V2 migration).
--       This migration only creates the customer_addresses table, which is owned
--       exclusively by the customer-service.
--
-- RLS: Row-Level Security is enabled on customer_addresses.
--      The policy uses the session variable `app.current_tenant` set by
--      TenantContextFilter before every request.
-- =============================================================================

-- ----------------------------------------------------------------------------
-- customer_addresses
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS customer_addresses (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID            NOT NULL,
    customer_id     UUID            NOT NULL,
    label           VARCHAR(50),
    address_line1   VARCHAR(200),
    address_line2   VARCHAR(200),
    city            VARCHAR(100),
    state           VARCHAR(100),
    pincode         VARCHAR(10),
    is_default      BOOLEAN         NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

-- Indexes for common query patterns
CREATE INDEX IF NOT EXISTS idx_customer_addresses_tenant_customer
    ON customer_addresses (tenant_id, customer_id);

CREATE INDEX IF NOT EXISTS idx_customer_addresses_tenant_id
    ON customer_addresses (tenant_id);

-- ----------------------------------------------------------------------------
-- Row-Level Security (RLS)
-- Ensures that queries only see rows belonging to the current tenant even if
-- a service bug omits the tenantId filter.
-- ----------------------------------------------------------------------------
ALTER TABLE customer_addresses ENABLE ROW LEVEL SECURITY;

-- Allow access only when the row's tenant_id matches the session variable
-- set by TenantContextFilter: SET app.current_tenant = '<uuid>'
CREATE POLICY IF NOT EXISTS customer_addresses_tenant_isolation
    ON customer_addresses
    USING (tenant_id = current_setting('app.current_tenant', true)::UUID);

-- Comment on table and key columns
COMMENT ON TABLE customer_addresses IS 'Saved shipping/billing addresses for customers. RLS-enforced multi-tenant.';
COMMENT ON COLUMN customer_addresses.tenant_id IS 'Owning tenant UUID — enforced by RLS policy.';
COMMENT ON COLUMN customer_addresses.customer_id IS 'FK to customers.id (auth-service). Not a DB-level FK to avoid cross-service constraints.';
COMMENT ON COLUMN customer_addresses.is_default IS 'At most one address per customer should have is_default=true. Enforced at service layer.';
