package in.supporthub.mcp.dto;

import java.util.List;

/**
 * Result returned by the {@code list_tickets} MCP tool.
 *
 * @param tickets list of summarised tickets for the customer
 * @param total   total number of tickets returned (may be less than requested limit)
 */
public record ListTicketsResult(
        List<TicketSummary> tickets,
        int total) {
}
