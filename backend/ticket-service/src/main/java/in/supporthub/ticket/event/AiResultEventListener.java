package in.supporthub.ticket.event;

import in.supporthub.shared.event.SentimentAnalysisCompletedEvent;
import in.supporthub.ticket.domain.SentimentLabel;
import in.supporthub.ticket.service.TicketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Listens for AI result events published by the ai-service and applies them to tickets.
 *
 * <p>Uses Redis-based idempotency to safely handle duplicate event deliveries.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class AiResultEventListener {

    private static final String IDEMPOTENCY_KEY_PREFIX = "processed:event:";
    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);

    private final TicketService ticketService;
    private final StringRedisTemplate redisTemplate;

    /**
     * Handles sentiment analysis completed events from the AI service.
     *
     * <p>Topic: {@code ai.sentiment-analysis-completed}
     *
     * @param event the sentiment analysis result
     */
    @KafkaListener(topics = "ai.sentiment-analysis-completed", groupId = "ticket-service")
    public void handleSentimentResult(SentimentAnalysisCompletedEvent event) {
        String idempotencyKey = IDEMPOTENCY_KEY_PREFIX + event.eventId();

        // Idempotency check — setIfAbsent returns true if the key was newly set
        Boolean isNewEvent = redisTemplate.opsForValue()
                .setIfAbsent(idempotencyKey, "1", IDEMPOTENCY_TTL);

        if (Boolean.FALSE.equals(isNewEvent)) {
            log.debug("Skipping duplicate sentiment event: eventId={}", event.eventId());
            return;
        }

        log.info("Processing sentiment result: eventId={}, ticketId={}, label={}",
                event.eventId(), event.payload().ticketId(), event.payload().sentimentLabel());

        try {
            SentimentLabel label = parseSentimentLabel(event.payload().sentimentLabel());
            float score = (float) event.payload().sentimentScore();
            ticketService.updateSentiment(event.payload().ticketId(), label, score);
        } catch (Exception ex) {
            log.error("Failed to apply sentiment result: eventId={}, ticketId={}, error={}",
                    event.eventId(), event.payload().ticketId(), ex.getMessage(), ex);
            // Remove idempotency key so the event can be retried
            redisTemplate.delete(idempotencyKey);
            throw ex;
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private SentimentLabel parseSentimentLabel(String rawLabel) {
        if (rawLabel == null) {
            return SentimentLabel.NEUTRAL;
        }
        // Normalise: "very_negative" → "VERY_NEGATIVE"
        try {
            return SentimentLabel.valueOf(rawLabel.toUpperCase().replace('-', '_'));
        } catch (IllegalArgumentException ex) {
            log.warn("Unknown sentiment label '{}', defaulting to NEUTRAL", rawLabel);
            return SentimentLabel.NEUTRAL;
        }
    }
}
