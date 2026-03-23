package in.supporthub.shared.event;

import java.time.Instant;

/**
 * Kafka event published when a new activity (comment, internal note, attachment) is added to a ticket.
 *
 * <p>Topic: {@code ticket.activity-added}
 *
 * <p>Consumers: notification-service (reply notifications), ai-service (conversation context update).
 */
public record TicketActivityAddedEvent(
        /** Unique event identifier — used for idempotency checks in consumers. */
        String eventId,

        /** Discriminator string matching the Kafka topic: "ticket.activity-added". */
        String eventType,

        /** Tenant identifier for multi-tenant routing. */
        String tenantId,

        /** Correlation ID propagated from the originating HTTP request. */
        String correlationId,

        /** When the activity was added — NOT when the event was published. */
        Instant occurredAt,

        /** Payload schema version. Increment on breaking payload changes. */
        String schemaVersion,

        /** Typed event payload. */
        Payload payload) {

    public static final String EVENT_TYPE = "ticket.activity-added";
    public static final String SCHEMA_VERSION = "1.0";

    /**
     * Convenience constructor that sets {@code eventType} and {@code schemaVersion} automatically.
     */
    public TicketActivityAddedEvent(
            String eventId,
            String tenantId,
            String correlationId,
            Instant occurredAt,
            Payload payload) {
        this(eventId, EVENT_TYPE, tenantId, correlationId, occurredAt, SCHEMA_VERSION, payload);
    }

    /**
     * Payload for the ticket-activity-added event.
     *
     * @param activityId        Internal UUID of the activity record.
     * @param ticketId          Internal UUID of the parent ticket.
     * @param ticketNumber      Human-readable ticket number.
     * @param customerId        UUID of the customer who owns the ticket.
     * @param assignedAgentId   UUID of the currently assigned agent (may be null).
     * @param actorId           UUID of the user who added the activity.
     * @param actorType         Type of actor: "AGENT", "CUSTOMER", or "SYSTEM".
     * @param activityType      Type of activity: "COMMENT", "INTERNAL_NOTE", "ATTACHMENT",
     *                          "STATUS_CHANGE", "ASSIGNMENT".
     * @param contentSnippet    First 500 chars of the activity content (no PII stripped here —
     *                          consumers must apply PII masking before sending to external systems).
     * @param isPublic          Whether the activity is visible to the customer ({@code false} = internal note).
     */
    public record Payload(
            String activityId,
            String ticketId,
            String ticketNumber,
            String customerId,
            String assignedAgentId,
            String actorId,
            String actorType,
            String activityType,
            String contentSnippet,
            boolean isPublic) {}
}
