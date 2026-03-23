package in.supporthub.mcp.dto;

import java.time.Instant;

/**
 * Detailed view of a single ticket, returned by the {@code get_ticket} MCP tool.
 *
 * @param ticketNumber  the ticket number (e.g. FC-2024-001234)
 * @param status        current status of the ticket
 * @param title         brief title / subject of the ticket
 * @param category      category slug (e.g. ORDER_ISSUE)
 * @param assignedAgent name or ID of the assigned agent, or {@code null} if unassigned
 * @param createdAt     timestamp when the ticket was created
 * @param lastActivity  human-readable description of the most recent activity on the ticket
 */
public record TicketDetailResult(
        String ticketNumber,
        String status,
        String title,
        String category,
        String assignedAgent,
        Instant createdAt,
        String lastActivity) {
}
