-- =============================================================================
-- V2__add_customer_profile_columns.sql
-- Customer-service schema: add profile columns to shared customers table
--
-- The `customers` table is owned by auth-service (V2 migration).
-- Customer-service extends it with display_name and timezone for profile mgmt.
-- =============================================================================

ALTER TABLE customers
    ADD COLUMN IF NOT EXISTS display_name  VARCHAR(150),
    ADD COLUMN IF NOT EXISTS timezone      VARCHAR(50);
