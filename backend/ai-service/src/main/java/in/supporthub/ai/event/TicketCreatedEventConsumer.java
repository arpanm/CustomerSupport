package in.supporthub.ai.event;

import in.supporthub.ai.service.SentimentAnalysisService;
import in.supporthub.shared.event.SentimentAnalysisCompletedEvent;
import in.supporthub.shared.event.TicketCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Kafka consumer that triggers sentiment analysis when a new ticket is created.
 *
 * <p>Idempotency is enforced via Redis: each {@code eventId} is recorded with a 24-hour TTL.
 * If the same event is received twice (redelivery after crash), it is silently discarded.
 *
 * <p>On successful analysis, publishes a {@link SentimentAnalysisCompletedEvent} to
 * {@code ai.results.sentiment} via {@link SentimentResultPublisher}.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class TicketCreatedEventConsumer {

    private static final String PROCESSED_KEY_PREFIX = "ai:processed:";
    private static final Duration PROCESSED_TTL = Duration.ofHours(24);

    private final SentimentAnalysisService sentimentAnalysisService;
    private final SentimentResultPublisher sentimentResultPublisher;
    private final StringRedisTemplate redisTemplate;

    /**
     * Processes a {@link TicketCreatedEvent} from the {@code ticket.created} Kafka topic.
     *
     * @param event the deserialized ticket-created event
     */
    @KafkaListener(topics = "ticket.created", groupId = "ai-service")
    public void onTicketCreated(TicketCreatedEvent event) {
        if (event == null || event.eventId() == null) {
            log.warn("Received null or invalid TicketCreatedEvent, skipping");
            return;
        }

        String idempotencyKey = PROCESSED_KEY_PREFIX + event.eventId();
        Boolean isNew = redisTemplate.opsForValue()
                .setIfAbsent(idempotencyKey, "1", PROCESSED_TTL);

        if (Boolean.FALSE.equals(isNew)) {
            log.debug("Duplicate event skipped: eventId={}, tenantId={}, ticketId={}",
                    event.eventId(), event.tenantId(), event.payload().ticketId());
            return;
        }

        log.info("Processing ticket.created event: eventId={}, tenantId={}, ticketId={}",
                event.eventId(), event.tenantId(), event.payload().ticketId());

        try {
            TicketCreatedEvent.Payload payload = event.payload();
            String textToAnalyse = buildTextForAnalysis(payload);

            var sentimentResult = sentimentAnalysisService.analyzeSentiment(
                    event.tenantId(),
                    payload.ticketId(),
                    textToAnalyse);

            SentimentAnalysisCompletedEvent completedEvent = new SentimentAnalysisCompletedEvent(
                    UUID.randomUUID().toString(),
                    event.tenantId(),
                    event.correlationId(),
                    Instant.now(),
                    new SentimentAnalysisCompletedEvent.Payload(
                            payload.ticketId(),
                            payload.ticketNumber(),
                            sentimentResult.score(),
                            sentimentResult.label(),
                            "claude-haiku-4-5-20251001",
                            null));

            sentimentResultPublisher.publish(event.tenantId(), payload.ticketId(), completedEvent);

        } catch (Exception ex) {
            log.error("Unexpected error processing ticket.created event: eventId={}, tenantId={}, ticketId={}",
                    event.eventId(), event.tenantId(), event.payload().ticketId(), ex);
            // Do not rethrow — let Kafka commit the offset to prevent poison-pill loops
        }
    }

    private String buildTextForAnalysis(TicketCreatedEvent.Payload payload) {
        String title = payload.title() != null ? payload.title() : "";
        String description = payload.descriptionSnippet() != null ? payload.descriptionSnippet() : "";
        return title + "\n" + description;
    }
}
