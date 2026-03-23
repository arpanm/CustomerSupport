package in.supporthub.shared.event;

import java.time.Instant;

/**
 * Kafka event published when a new support ticket is created.
 *
 * <p>Topic: {@code ticket.created}
 *
 * <p>Consumers: ai-service (sentiment analysis), notification-service (customer acknowledgement),
 * reporting-service (metrics).
 */
public record TicketCreatedEvent(
        /** Unique event identifier — used for idempotency checks in consumers. */
        String eventId,

        /** Discriminator string matching the Kafka topic: "ticket.created". */
        String eventType,

        /** Tenant identifier for multi-tenant routing. */
        String tenantId,

        /** Correlation ID propagated from the originating HTTP request. */
        String correlationId,

        /** When the ticket creation occurred — NOT when the event was published. */
        Instant occurredAt,

        /** Payload schema version. Increment on breaking payload changes. */
        String schemaVersion,

        /** Typed event payload. */
        Payload payload) {

    public static final String EVENT_TYPE = "ticket.created";
    public static final String SCHEMA_VERSION = "1.0";

    /**
     * Convenience constructor that sets {@code eventType} and {@code schemaVersion} automatically.
     */
    public TicketCreatedEvent(
            String eventId,
            String tenantId,
            String correlationId,
            Instant occurredAt,
            Payload payload) {
        this(eventId, EVENT_TYPE, tenantId, correlationId, occurredAt, SCHEMA_VERSION, payload);
    }

    /**
     * Payload for the ticket-created event.
     *
     * @param ticketId          Internal UUID of the ticket.
     * @param ticketNumber      Human-readable ticket number (e.g., "FC-2024-001234").
     * @param customerId        UUID of the customer who raised the ticket.
     * @param categoryId        UUID of the assigned category.
     * @param subCategoryId     UUID of the optional sub-category (may be null).
     * @param title             Ticket title (≤ 200 chars).
     * @param descriptionSnippet First 500 chars of the description (for AI pre-processing).
     * @param channel           Originating channel (e.g., "web", "whatsapp", "email").
     * @param priority          Initial priority (e.g., "LOW", "MEDIUM", "HIGH", "CRITICAL").
     */
    public record Payload(
            String ticketId,
            String ticketNumber,
            String customerId,
            String categoryId,
            String subCategoryId,
            String title,
            String descriptionSnippet,
            String channel,
            String priority) {}
}
