package in.supporthub.notification.event;

import in.supporthub.notification.service.NotificationService;
import in.supporthub.shared.event.TicketCreatedEvent;
import in.supporthub.shared.event.TicketStatusChangedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Kafka consumer for ticket domain events.
 *
 * <p>Listens on {@code ticket.created} and {@code ticket.status-changed} topics.
 * Uses Redis-backed idempotency to prevent duplicate notification delivery
 * on Kafka consumer rebalances or retries.
 *
 * <p>Idempotency key format: {@code notif:processed:{eventId}} — TTL 24 hours.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class TicketEventConsumer {

    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);
    private static final String IDEMPOTENCY_KEY_PREFIX = "notif:processed:";

    private final NotificationService notificationService;
    private final StringRedisTemplate redisTemplate;

    /**
     * Handles {@code ticket.created} events.
     *
     * <p>Sends SMS and WhatsApp to the customer with their new ticket number.
     * Also creates an in-app notification for the customer.
     *
     * @param event Deserialized ticket-created event.
     */
    @KafkaListener(
            topics = "ticket.created",
            groupId = "notification-service",
            containerFactory = "kafkaListenerContainerFactory")
    public void onTicketCreated(TicketCreatedEvent event) {
        String eventId = event.eventId();
        String idempotencyKey = IDEMPOTENCY_KEY_PREFIX + eventId;

        Boolean isNew = redisTemplate.opsForValue()
                .setIfAbsent(idempotencyKey, "1", IDEMPOTENCY_TTL);

        if (Boolean.FALSE.equals(isNew)) {
            log.debug("Skipping duplicate ticket.created event eventId={}", eventId);
            return;
        }

        log.info("Processing ticket.created event eventId={} tenantId={} ticketNumber={}",
                eventId, event.tenantId(), event.payload().ticketNumber());

        try {
            notificationService.sendTicketCreatedNotification(
                    event.tenantId(),
                    event.payload().customerId(),
                    event.payload().ticketNumber(),
                    event.payload().categoryId()
            );
        } catch (Exception e) {
            log.error("Failed to process ticket.created notification eventId={} tenantId={}: {}",
                    eventId, event.tenantId(), e.getMessage());
            // Re-throw to trigger Kafka retry/DLT
            throw e;
        }
    }

    /**
     * Handles {@code ticket.status-changed} events.
     *
     * <p>Notifies:
     * <ul>
     *   <li>Customer when status changes to RESOLVED.</li>
     *   <li>Assigned agent when ticket is ESCALATED.</li>
     * </ul>
     *
     * @param event Deserialized ticket-status-changed event.
     */
    @KafkaListener(
            topics = "ticket.status-changed",
            groupId = "notification-service",
            containerFactory = "kafkaListenerContainerFactory")
    public void onTicketStatusChanged(TicketStatusChangedEvent event) {
        String eventId = event.eventId();
        String idempotencyKey = IDEMPOTENCY_KEY_PREFIX + eventId;

        Boolean isNew = redisTemplate.opsForValue()
                .setIfAbsent(idempotencyKey, "1", IDEMPOTENCY_TTL);

        if (Boolean.FALSE.equals(isNew)) {
            log.debug("Skipping duplicate ticket.status-changed event eventId={}", eventId);
            return;
        }

        log.info("Processing ticket.status-changed event eventId={} tenantId={} ticketNumber={} newStatus={}",
                eventId, event.tenantId(), event.payload().ticketNumber(), event.payload().newStatus());

        try {
            String newStatus = event.payload().newStatus();
            String tenantId = event.tenantId();
            String ticketNumber = event.payload().ticketNumber();
            String previousStatus = event.payload().previousStatus();

            if ("RESOLVED".equals(newStatus)) {
                // Notify the customer their ticket has been resolved
                notificationService.sendStatusChangedNotification(
                        tenantId,
                        event.payload().customerId(),
                        in.supporthub.notification.domain.Notification.RecipientType.CUSTOMER,
                        previousStatus,
                        newStatus,
                        ticketNumber,
                        event.payload().ticketId()
                );
            } else if ("ESCALATED".equals(newStatus) && event.payload().assignedAgentId() != null) {
                // Notify the assigned agent of escalation
                notificationService.sendStatusChangedNotification(
                        tenantId,
                        event.payload().assignedAgentId(),
                        in.supporthub.notification.domain.Notification.RecipientType.AGENT,
                        previousStatus,
                        newStatus,
                        ticketNumber,
                        event.payload().ticketId()
                );
            } else {
                log.debug("No notification rule matched for status transition {} -> {} ticketNumber={}",
                        previousStatus, newStatus, ticketNumber);
            }
        } catch (Exception e) {
            log.error("Failed to process ticket.status-changed notification eventId={} tenantId={}: {}",
                    eventId, event.tenantId(), e.getMessage());
            throw e;
        }
    }
}
