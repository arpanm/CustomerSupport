package in.supporthub.tenant.domain;

/**
 * Subscription plan types available for SupportHub tenants.
 *
 * <p>The plan type determines available features, SLA defaults, and rate limits.
 */
public enum PlanType {

    /** Free tier — limited agents, basic features, community SLA. */
    FREE,

    /** Starter tier — small teams, standard SLA, email support. */
    STARTER,

    /** Growth tier — growing teams, priority SLA, multi-channel support. */
    GROWTH,

    /** Enterprise tier — unlimited agents, custom SLA, dedicated support. */
    ENTERPRISE
}
