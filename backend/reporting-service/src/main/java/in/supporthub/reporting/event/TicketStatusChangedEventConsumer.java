package in.supporthub.reporting.event;

import in.supporthub.reporting.service.ReportingProjectionService;
import in.supporthub.shared.event.TicketStatusChangedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Kafka consumer for {@code ticket.status-changed} events.
 *
 * <p>Updates the {@link in.supporthub.reporting.domain.TicketDocument} status in Elasticsearch.
 * When the new status is {@code RESOLVED}, the resolution time is computed and stored.
 *
 * <p>Consumed topic: {@code ticket.status-changed}
 * <p>Consumer group: {@code reporting-service}
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class TicketStatusChangedEventConsumer {

    private static final String IDEMPOTENCY_KEY_PREFIX = "reporting:processed:";
    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);

    private final ReportingProjectionService projectionService;
    private final StringRedisTemplate redisTemplate;

    /**
     * Handles a ticket-status-changed Kafka event.
     *
     * @param event the deserialized {@link TicketStatusChangedEvent}
     */
    @KafkaListener(topics = "ticket.status-changed", groupId = "reporting-service")
    public void handle(TicketStatusChangedEvent event) {
        String idempotencyKey = IDEMPOTENCY_KEY_PREFIX + event.eventId();

        Boolean isNew = redisTemplate.opsForValue()
                .setIfAbsent(idempotencyKey, "1", IDEMPOTENCY_TTL);

        if (Boolean.FALSE.equals(isNew)) {
            log.debug("Skipping duplicate ticket.status-changed event: eventId={}, ticketId={}",
                    event.eventId(), event.payload().ticketId());
            return;
        }

        TicketStatusChangedEvent.Payload payload = event.payload();

        log.info("Processing ticket.status-changed event: eventId={}, tenantId={}, ticketId={}, "
                        + "oldStatus={}, newStatus={}",
                event.eventId(), event.tenantId(), payload.ticketId(),
                payload.previousStatus(), payload.newStatus());

        projectionService.updateTicketStatus(
                event.tenantId(),
                payload.ticketId(),
                payload.newStatus(),
                payload.assignedAgentId(),
                event.occurredAt()
        );
    }
}
