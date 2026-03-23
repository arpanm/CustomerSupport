package in.supporthub.shared.event;

import java.time.Instant;

/**
 * Kafka event published by the AI service when sentiment analysis for a ticket completes.
 *
 * <p>Topic: {@code ai.sentiment-analysis-completed}
 *
 * <p>Consumers: ticket-service (persists sentiment columns), reporting-service (sentiment metrics),
 * notification-service (escalate on very_negative sentiment).
 */
public record SentimentAnalysisCompletedEvent(
        /** Unique event identifier — used for idempotency checks in consumers. */
        String eventId,

        /** Discriminator string: "ai.sentiment-analysis-completed". */
        String eventType,

        /** Tenant identifier for multi-tenant routing. */
        String tenantId,

        /** Correlation ID — propagated from the originating ticket.created event. */
        String correlationId,

        /** When the analysis completed — NOT when the event was published. */
        Instant occurredAt,

        /** Payload schema version. Increment on breaking payload changes. */
        String schemaVersion,

        /** Typed event payload. */
        Payload payload) {

    public static final String EVENT_TYPE = "ai.sentiment-analysis-completed";
    public static final String SCHEMA_VERSION = "1.0";

    /**
     * Convenience constructor that sets {@code eventType} and {@code schemaVersion} automatically.
     */
    public SentimentAnalysisCompletedEvent(
            String eventId,
            String tenantId,
            String correlationId,
            Instant occurredAt,
            Payload payload) {
        this(eventId, EVENT_TYPE, tenantId, correlationId, occurredAt, SCHEMA_VERSION, payload);
    }

    /**
     * Payload for the sentiment-analysis-completed event.
     *
     * @param ticketId       Internal UUID of the analysed ticket.
     * @param ticketNumber   Human-readable ticket number.
     * @param sentimentScore Normalised score from -1.0 (very_negative) to 1.0 (very_positive).
     * @param sentimentLabel One of: "very_negative", "negative", "neutral", "positive",
     *                       "very_positive".
     * @param modelId        Identifier of the AI model used (e.g., "claude-haiku-4-5-20251001").
     * @param langfuseTraceId Optional Langfuse trace ID for observability.
     */
    public record Payload(
            String ticketId,
            String ticketNumber,
            double sentimentScore,
            String sentimentLabel,
            String modelId,
            String langfuseTraceId) {}
}
