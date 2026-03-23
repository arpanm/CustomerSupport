package in.supporthub.ticket.service;

import in.supporthub.shared.event.TicketActivityAddedEvent;
import in.supporthub.shared.event.TicketCreatedEvent;
import in.supporthub.shared.event.TicketStatusChangedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

/**
 * Publishes ticket-related Kafka events to the appropriate topics.
 *
 * <p>Uses the event records from {@code supporthub-shared} — business code must
 * never use raw {@link KafkaTemplate} directly.
 *
 * <p>Topics:
 * <ul>
 *   <li>{@code ticket.created}</li>
 *   <li>{@code ticket.status-changed}</li>
 *   <li>{@code ticket.activity-added}</li>
 * </ul>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TicketEventPublisher {

    static final String TOPIC_TICKET_CREATED = "ticket.created";
    static final String TOPIC_STATUS_CHANGED = "ticket.status-changed";
    static final String TOPIC_ACTIVITY_ADDED = "ticket.activity-added";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * Publishes a {@code ticket.created} event.
     *
     * @param event the event record populated by the caller
     */
    public void publishTicketCreated(TicketCreatedEvent event) {
        kafkaTemplate.send(TOPIC_TICKET_CREATED, event.payload().ticketId(), event);
        log.info("Published ticket.created: ticketId={}, tenantId={}, ticketNumber={}",
                event.payload().ticketId(), event.tenantId(), event.payload().ticketNumber());
    }

    /**
     * Publishes a {@code ticket.status-changed} event.
     *
     * @param event the event record populated by the caller
     */
    public void publishStatusChanged(TicketStatusChangedEvent event) {
        kafkaTemplate.send(TOPIC_STATUS_CHANGED, event.payload().ticketId(), event);
        log.info("Published ticket.status-changed: ticketId={}, tenantId={}, status={} → {}",
                event.payload().ticketId(), event.tenantId(),
                event.payload().previousStatus(), event.payload().newStatus());
    }

    /**
     * Publishes a {@code ticket.activity-added} event.
     *
     * @param event the event record populated by the caller
     */
    public void publishActivityAdded(TicketActivityAddedEvent event) {
        kafkaTemplate.send(TOPIC_ACTIVITY_ADDED, event.payload().ticketId(), event);
        log.info("Published ticket.activity-added: ticketId={}, tenantId={}, activityType={}",
                event.payload().ticketId(), event.tenantId(), event.payload().activityType());
    }
}
