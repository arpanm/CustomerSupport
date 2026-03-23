package in.supporthub.shared.event;

import java.time.Instant;

/**
 * Kafka event published whenever a ticket's status transitions.
 *
 * <p>Topic: {@code ticket.status-changed}
 *
 * <p>Consumers: notification-service (agent / customer notifications),
 * reporting-service (SLA metrics), ai-service (resolution feedback).
 */
public record TicketStatusChangedEvent(
        /** Unique event identifier — used for idempotency checks in consumers. */
        String eventId,

        /** Discriminator string matching the Kafka topic: "ticket.status-changed". */
        String eventType,

        /** Tenant identifier for multi-tenant routing. */
        String tenantId,

        /** Correlation ID propagated from the originating HTTP request. */
        String correlationId,

        /** When the status transition occurred — NOT when the event was published. */
        Instant occurredAt,

        /** Payload schema version. Increment on breaking payload changes. */
        String schemaVersion,

        /** Typed event payload. */
        Payload payload) {

    public static final String EVENT_TYPE = "ticket.status-changed";
    public static final String SCHEMA_VERSION = "1.0";

    /**
     * Convenience constructor that sets {@code eventType} and {@code schemaVersion} automatically.
     */
    public TicketStatusChangedEvent(
            String eventId,
            String tenantId,
            String correlationId,
            Instant occurredAt,
            Payload payload) {
        this(eventId, EVENT_TYPE, tenantId, correlationId, occurredAt, SCHEMA_VERSION, payload);
    }

    /**
     * Payload for the ticket-status-changed event.
     *
     * @param ticketId          Internal UUID of the ticket.
     * @param ticketNumber      Human-readable ticket number.
     * @param customerId        UUID of the customer who owns the ticket.
     * @param assignedAgentId   UUID of the currently assigned agent (may be null if unassigned).
     * @param previousStatus    Status before the transition (e.g., "OPEN").
     * @param newStatus         Status after the transition (e.g., "IN_PROGRESS").
     * @param changedByUserId   UUID of the user who triggered the status change.
     * @param changedByUserType Type of user who triggered the change ("AGENT", "CUSTOMER", "SYSTEM").
     * @param resolutionNote    Optional resolution note (populated when status becomes "RESOLVED").
     */
    public record Payload(
            String ticketId,
            String ticketNumber,
            String customerId,
            String assignedAgentId,
            String previousStatus,
            String newStatus,
            String changedByUserId,
            String changedByUserType,
            String resolutionNote) {}
}
