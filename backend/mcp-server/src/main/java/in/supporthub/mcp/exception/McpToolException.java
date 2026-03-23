package in.supporthub.mcp.exception;

/**
 * Exception thrown by MCP tool implementations when a downstream service call fails
 * or an input cannot be processed.
 *
 * <p>Unlike HTTP-layer exceptions, this exception is caught inside the tool method
 * itself and converted into a descriptive error message that the AI agent can relay
 * to the user. It is intentionally NOT a subclass of {@code AppException} to avoid
 * mapping it to HTTP 4xx/5xx responses.
 *
 * <p>Usage:
 * <pre>{@code
 * try {
 *     return ticketServiceClient.getTicket(tenantId, ticketNumber);
 * } catch (McpToolException ex) {
 *     return new TicketDetailResult(ticketNumber, "ERROR", null, null, null, null, ex.getMessage());
 * }
 * }</pre>
 */
public class McpToolException extends RuntimeException {

    /**
     * Constructs a new {@code McpToolException} with a user-friendly message.
     *
     * @param message descriptive message suitable for relaying to the customer
     */
    public McpToolException(String message) {
        super(message);
    }

    /**
     * Constructs a new {@code McpToolException} with a user-friendly message and cause.
     *
     * @param message descriptive message suitable for relaying to the customer
     * @param cause   the underlying exception that triggered this error
     */
    public McpToolException(String message, Throwable cause) {
        super(message, cause);
    }
}
