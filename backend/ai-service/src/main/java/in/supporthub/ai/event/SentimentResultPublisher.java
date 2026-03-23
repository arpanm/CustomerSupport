package in.supporthub.ai.event;

import in.supporthub.shared.event.SentimentAnalysisCompletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Kafka producer that publishes {@link SentimentAnalysisCompletedEvent} to the
 * {@code ai.results.sentiment} topic.
 *
 * <p>The ticket ID is used as the Kafka message key to ensure all sentiment events for the same
 * ticket land on the same partition (preserving order within a ticket).
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class SentimentResultPublisher {

    static final String TOPIC = "ai.results.sentiment";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * Publishes a completed sentiment analysis event.
     *
     * @param tenantId the owning tenant UUID (used for logging)
     * @param ticketId the ticket UUID — used as the Kafka message key
     * @param event    the event to publish
     */
    public void publish(String tenantId, String ticketId, SentimentAnalysisCompletedEvent event) {
        try {
            kafkaTemplate.send(TOPIC, ticketId, event);
            log.info("Published SentimentAnalysisCompletedEvent: tenantId={}, ticketId={}, label={}",
                    tenantId, ticketId, event.payload().sentimentLabel());
        } catch (Exception ex) {
            log.error("Failed to publish SentimentAnalysisCompletedEvent: tenantId={}, ticketId={}",
                    tenantId, ticketId, ex);
        }
    }
}
