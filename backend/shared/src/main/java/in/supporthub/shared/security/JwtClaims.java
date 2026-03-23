package in.supporthub.shared.security;

/**
 * Typed representation of the standard claims extracted from a SupportHub JWT.
 *
 * <p>All SupportHub JWTs (customer, agent, MCP) include these claims. The auth-service
 * issues tokens; each other service's security filter parses and validates them.
 *
 * <p>Claim mapping:
 * <ul>
 *   <li>{@code sub}      — standard JWT subject; contains the user's UUID.</li>
 *   <li>{@code tenantId} — custom claim; UUID of the tenant this user belongs to.</li>
 *   <li>{@code role}     — custom claim; user's role within the tenant
 *                          (e.g., "CUSTOMER", "AGENT", "ADMIN", "SUPER_ADMIN").</li>
 *   <li>{@code type}     — custom claim; token type: "customer", "agent", or "mcp".</li>
 * </ul>
 *
 * @param sub      Subject — UUID of the authenticated user.
 * @param tenantId UUID of the tenant this token was issued for.
 * @param role     Role of the token holder within the tenant.
 * @param type     Token type — determines which endpoints the token may access.
 */
public record JwtClaims(
        String sub,
        String tenantId,
        String role,
        String type) {

    /** Token type constant for customer-portal tokens. */
    public static final String TYPE_CUSTOMER = "customer";

    /** Token type constant for agent-dashboard and admin-portal tokens. */
    public static final String TYPE_AGENT = "agent";

    /** Token type constant for MCP server tokens. */
    public static final String TYPE_MCP = "mcp";

    /** Role constant for regular customers. */
    public static final String ROLE_CUSTOMER = "CUSTOMER";

    /** Role constant for support agents. */
    public static final String ROLE_AGENT = "AGENT";

    /** Role constant for tenant administrators. */
    public static final String ROLE_ADMIN = "ADMIN";

    /** Role constant for platform-level super admins. */
    public static final String ROLE_SUPER_ADMIN = "SUPER_ADMIN";

    /**
     * Returns {@code true} if this token belongs to a customer.
     */
    public boolean isCustomer() {
        return TYPE_CUSTOMER.equals(type);
    }

    /**
     * Returns {@code true} if this token belongs to an agent (includes admins).
     */
    public boolean isAgent() {
        return TYPE_AGENT.equals(type);
    }

    /**
     * Returns {@code true} if this token has admin or super-admin role.
     */
    public boolean isAdmin() {
        return ROLE_ADMIN.equals(role) || ROLE_SUPER_ADMIN.equals(role);
    }

    /**
     * Returns {@code true} if this token has super-admin role.
     */
    public boolean isSuperAdmin() {
        return ROLE_SUPER_ADMIN.equals(role);
    }
}
