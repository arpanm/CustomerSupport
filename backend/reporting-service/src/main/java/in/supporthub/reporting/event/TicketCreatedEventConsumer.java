package in.supporthub.reporting.event;

import in.supporthub.reporting.domain.TicketDocument;
import in.supporthub.reporting.service.ReportingProjectionService;
import in.supporthub.shared.event.TicketCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Kafka consumer for {@code ticket.created} events.
 *
 * <p>On receipt, builds a {@link TicketDocument} and upserts it in Elasticsearch.
 * Idempotency is enforced via a Redis key with a 24-hour TTL so that duplicate
 * Kafka deliveries do not create duplicate documents.
 *
 * <p>Consumed topic: {@code ticket.created}
 * <p>Consumer group: {@code reporting-service}
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class TicketCreatedEventConsumer {

    private static final String IDEMPOTENCY_KEY_PREFIX = "reporting:processed:";
    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);

    private final ReportingProjectionService projectionService;
    private final StringRedisTemplate redisTemplate;

    /**
     * Handles a ticket-created Kafka event.
     *
     * @param event the deserialized {@link TicketCreatedEvent}
     */
    @KafkaListener(topics = "ticket.created", groupId = "reporting-service")
    public void handle(TicketCreatedEvent event) {
        String idempotencyKey = IDEMPOTENCY_KEY_PREFIX + event.eventId();

        Boolean isNew = redisTemplate.opsForValue()
                .setIfAbsent(idempotencyKey, "1", IDEMPOTENCY_TTL);

        if (Boolean.FALSE.equals(isNew)) {
            log.debug("Skipping duplicate ticket.created event: eventId={}, ticketId={}",
                    event.eventId(), event.payload().ticketId());
            return;
        }

        log.info("Processing ticket.created event: eventId={}, tenantId={}, ticketId={}",
                event.eventId(), event.tenantId(), event.payload().ticketId());

        TicketDocument doc = buildDocument(event);
        projectionService.upsertTicket(doc);

        log.info("Ticket document created in Elasticsearch: ticketId={}, tenantId={}",
                event.payload().ticketId(), event.tenantId());
    }

    private TicketDocument buildDocument(TicketCreatedEvent event) {
        TicketCreatedEvent.Payload payload = event.payload();

        TicketDocument doc = new TicketDocument();
        doc.setId(payload.ticketId());
        doc.setTenantId(event.tenantId());
        doc.setTicketNumber(payload.ticketNumber());
        doc.setStatus("OPEN");
        doc.setPriority(payload.priority());
        doc.setChannel(payload.channel());
        doc.setCategoryId(payload.categoryId());
        doc.setCustomerId(payload.customerId());
        doc.setSlaBreached(Boolean.FALSE);
        doc.setCreatedAt(event.occurredAt());
        doc.setUpdatedAt(Instant.now());

        return doc;
    }
}
