package in.supporthub.tenant.domain;

/**
 * Lifecycle status of a SupportHub tenant.
 */
public enum TenantStatus {

    /** Tenant has been created but onboarding is not yet complete. */
    PENDING,

    /** Tenant is fully operational. */
    ACTIVE,

    /** Tenant has been suspended — all access is blocked. */
    SUSPENDED
}
