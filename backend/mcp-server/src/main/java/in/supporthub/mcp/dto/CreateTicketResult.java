package in.supporthub.mcp.dto;

/**
 * Result returned by the {@code create_ticket} MCP tool.
 *
 * @param ticketNumber the generated ticket number (e.g. FC-2024-001234)
 * @param status       the initial status of the newly created ticket
 * @param message      human-readable message for the AI agent to relay to the customer
 */
public record CreateTicketResult(
        String ticketNumber,
        String status,
        String message) {
}
