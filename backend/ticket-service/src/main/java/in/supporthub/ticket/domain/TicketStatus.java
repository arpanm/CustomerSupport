package in.supporthub.ticket.domain;

import java.util.Map;
import java.util.Set;

/**
 * Represents the lifecycle states of a support ticket.
 *
 * <p>The valid transition graph:
 * <pre>
 * OPEN → PENDING_AGENT_RESPONSE | IN_PROGRESS | ESCALATED
 * PENDING_AGENT_RESPONSE → PENDING_CUSTOMER_RESPONSE | IN_PROGRESS
 * PENDING_CUSTOMER_RESPONSE → IN_PROGRESS | PENDING_AGENT_RESPONSE
 * IN_PROGRESS → RESOLVED | ESCALATED | PENDING_CUSTOMER_RESPONSE
 * ESCALATED → IN_PROGRESS | RESOLVED
 * RESOLVED → CLOSED | REOPENED
 * CLOSED → REOPENED
 * REOPENED → IN_PROGRESS | RESOLVED
 * </pre>
 */
public enum TicketStatus {

    OPEN,
    PENDING_AGENT_RESPONSE,
    PENDING_CUSTOMER_RESPONSE,
    IN_PROGRESS,
    ESCALATED,
    RESOLVED,
    CLOSED,
    REOPENED;

    private static final Map<TicketStatus, Set<TicketStatus>> VALID_TRANSITIONS = Map.of(
            OPEN, Set.of(PENDING_AGENT_RESPONSE, IN_PROGRESS, ESCALATED),
            PENDING_AGENT_RESPONSE, Set.of(PENDING_CUSTOMER_RESPONSE, IN_PROGRESS),
            PENDING_CUSTOMER_RESPONSE, Set.of(IN_PROGRESS, PENDING_AGENT_RESPONSE, RESOLVED),
            IN_PROGRESS, Set.of(RESOLVED, ESCALATED, PENDING_CUSTOMER_RESPONSE),
            ESCALATED, Set.of(IN_PROGRESS, RESOLVED),
            RESOLVED, Set.of(CLOSED, REOPENED),
            CLOSED, Set.of(REOPENED),
            REOPENED, Set.of(IN_PROGRESS, RESOLVED)
    );

    /**
     * Returns {@code true} if this status can transition to {@code next}.
     *
     * @param next the target status
     * @return {@code true} when the transition is valid; {@code false} otherwise
     */
    public boolean canTransitionTo(TicketStatus next) {
        return VALID_TRANSITIONS.getOrDefault(this, Set.of()).contains(next);
    }

    /**
     * Returns the set of statuses reachable from this status.
     *
     * @return immutable set of valid next statuses
     */
    public Set<TicketStatus> allowedTransitions() {
        return VALID_TRANSITIONS.getOrDefault(this, Set.of());
    }

    /**
     * Returns {@code true} if this is a terminal status (no further transitions possible
     * except reopening).
     */
    public boolean isTerminal() {
        return this == RESOLVED || this == CLOSED;
    }
}
