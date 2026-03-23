package in.supporthub.mcp.dto;

import java.time.Instant;

/**
 * Summarised view of a single ticket, used inside {@link ListTicketsResult}.
 *
 * @param ticketNumber the ticket number (e.g. FC-2024-001234)
 * @param status       current status of the ticket
 * @param title        brief title / subject of the ticket
 * @param createdAt    timestamp when the ticket was created
 */
public record TicketSummary(
        String ticketNumber,
        String status,
        String title,
        Instant createdAt) {
}
