package in.supporthub.ticket.websocket;

import java.time.Instant;

/**
 * WebSocket message payload published to STOMP topic
 * {@code /topic/tenant/{tenantId}/tickets} when any ticket-related event occurs.
 *
 * <p>Consumers are agent-dashboard and admin-portal clients connected via SockJS/STOMP.
 * This record is immutable and safe for concurrent serialization.
 *
 * @param ticketId     Internal UUID of the ticket.
 * @param ticketNumber Human-readable ticket number (e.g., "FC-2024-001234").
 * @param eventType    Discriminator: CREATED, STATUS_CHANGED, ACTIVITY_ADDED, SLA_BREACHED.
 * @param newStatus    Current status string after the event (may equal previous for ACTIVITY_ADDED).
 * @param assigneeId   UUID of the currently assigned agent — {@code null} if unassigned.
 * @param title        Ticket title (not the full description — safe for broadcast).
 * @param priority     Priority label: LOW, MEDIUM, HIGH, CRITICAL.
 * @param occurredAt   Timestamp when the domain event occurred.
 */
public record TicketUpdateMessage(
        String ticketId,
        String ticketNumber,
        String eventType,
        String newStatus,
        String assigneeId,
        String title,
        String priority,
        Instant occurredAt
) {}
