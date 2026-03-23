package in.supporthub.auth.domain;

/**
 * Roles available to agent users within a tenant.
 *
 * <p>Role hierarchy (ascending privilege):
 * AGENT < TEAM_LEAD < ADMIN < SUPER_ADMIN
 */
public enum AgentRole {

    /** Regular support agent — can handle tickets assigned to them. */
    AGENT,

    /** Team lead — can assign tickets, view team metrics. */
    TEAM_LEAD,

    /** Tenant administrator — can manage agents, categories, and tenant config. */
    ADMIN,

    /** Platform-level super admin — full access across all tenants. */
    SUPER_ADMIN
}
