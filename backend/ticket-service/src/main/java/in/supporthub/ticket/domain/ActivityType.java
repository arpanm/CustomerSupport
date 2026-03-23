package in.supporthub.ticket.domain;

/**
 * The type of activity recorded on a ticket.
 */
public enum ActivityType {
    CUSTOMER_COMMENT,
    AGENT_COMMENT,
    AGENT_NOTE,
    STATUS_CHANGE,
    ASSIGNMENT_CHANGE,
    RESOLUTION,
    SYSTEM
}
