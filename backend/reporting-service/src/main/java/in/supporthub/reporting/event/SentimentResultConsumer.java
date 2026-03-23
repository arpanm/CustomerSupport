package in.supporthub.reporting.event;

import in.supporthub.reporting.service.ReportingProjectionService;
import in.supporthub.shared.event.SentimentAnalysisCompletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Kafka consumer for {@code ai.sentiment-analysis-completed} events.
 *
 * <p>Updates the {@code sentimentLabel} and {@code sentimentScore} fields on the
 * corresponding {@link in.supporthub.reporting.domain.TicketDocument} in Elasticsearch.
 *
 * <p>Consumed topic: {@code ai.sentiment-analysis-completed}
 * <p>Consumer group: {@code reporting-service}
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class SentimentResultConsumer {

    private static final String IDEMPOTENCY_KEY_PREFIX = "reporting:processed:";
    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);

    private final ReportingProjectionService projectionService;
    private final StringRedisTemplate redisTemplate;

    /**
     * Handles a sentiment-analysis-completed Kafka event.
     *
     * @param event the deserialized {@link SentimentAnalysisCompletedEvent}
     */
    @KafkaListener(topics = "ai.sentiment-analysis-completed", groupId = "reporting-service")
    public void handle(SentimentAnalysisCompletedEvent event) {
        String idempotencyKey = IDEMPOTENCY_KEY_PREFIX + event.eventId();

        Boolean isNew = redisTemplate.opsForValue()
                .setIfAbsent(idempotencyKey, "1", IDEMPOTENCY_TTL);

        if (Boolean.FALSE.equals(isNew)) {
            log.debug("Skipping duplicate ai.sentiment-analysis-completed event: eventId={}, ticketId={}",
                    event.eventId(), event.payload().ticketId());
            return;
        }

        SentimentAnalysisCompletedEvent.Payload payload = event.payload();

        log.info("Processing sentiment result: eventId={}, tenantId={}, ticketId={}, label={}",
                event.eventId(), event.tenantId(), payload.ticketId(), payload.sentimentLabel());

        projectionService.updateSentiment(
                event.tenantId(),
                payload.ticketId(),
                payload.sentimentLabel(),
                payload.sentimentScore()
        );
    }
}
