package in.supporthub.shared.security;

/**
 * ThreadLocal holder for the current request's tenant identifier.
 *
 * <p>The {@code TenantContextFilter} (in each service) validates the tenant from the JWT and
 * populates this holder before any business logic runs. Services MUST read the tenant ID from
 * here — NEVER accept it as a user-controlled request parameter.
 *
 * <p>Virtual-thread note: Spring Boot 3.3 with
 * {@code spring.threads.virtual.enabled=true} uses virtual threads for each request. Each virtual
 * thread has its own {@link ThreadLocal}, so this holder is safe under virtual threads as long as
 * it is cleared via {@link #clear()} at the end of the request (handled by the filter).
 *
 * <p>Usage in a filter:
 * <pre>{@code
 * TenantContextHolder.setTenantId(validatedTenantId);
 * try {
 *     filterChain.doFilter(request, response);
 * } finally {
 *     TenantContextHolder.clear();
 * }
 * }</pre>
 *
 * <p>Usage in a service:
 * <pre>{@code
 * String tenantId = TenantContextHolder.getTenantId();
 * }</pre>
 */
public final class TenantContextHolder {

    private static final ThreadLocal<String> TENANT_ID_HOLDER = new ThreadLocal<>();

    private TenantContextHolder() {
        // Utility class — do not instantiate
    }

    /**
     * Returns the tenant ID set for the current thread/request.
     *
     * @return tenant ID string (UUID format)
     * @throws IllegalStateException if called before the tenant context filter has populated it
     */
    public static String getTenantId() {
        String tenantId = TENANT_ID_HOLDER.get();
        if (tenantId == null) {
            throw new IllegalStateException(
                    "Tenant context is not set. Ensure TenantContextFilter runs before business logic.");
        }
        return tenantId;
    }

    /**
     * Returns the tenant ID, or {@code null} if not set (for optional/public endpoints).
     *
     * @return tenant ID string, or {@code null}
     */
    public static String getTenantIdOrNull() {
        return TENANT_ID_HOLDER.get();
    }

    /**
     * Sets the tenant ID for the current thread. Called by the tenant context filter.
     *
     * @param tenantId validated tenant UUID string — must not be null or blank
     */
    public static void setTenantId(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId must not be null or blank");
        }
        TENANT_ID_HOLDER.set(tenantId);
    }

    /**
     * Clears the tenant ID from the current thread. MUST be called in the {@code finally} block
     * of the tenant context filter to prevent tenant leakage across requests.
     */
    public static void clear() {
        TENANT_ID_HOLDER.remove();
    }
}
