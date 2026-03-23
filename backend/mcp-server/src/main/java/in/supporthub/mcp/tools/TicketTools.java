package in.supporthub.mcp.tools;

import in.supporthub.mcp.client.TicketServiceClient;
import in.supporthub.mcp.dto.CreateTicketResult;
import in.supporthub.mcp.dto.ListTicketsResult;
import in.supporthub.mcp.dto.TicketDetailResult;
import in.supporthub.mcp.exception.McpToolException;
import in.supporthub.shared.security.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * MCP tool implementations for ticket operations.
 *
 * <p>Each public method is annotated with {@link Tool} and will be registered as
 * an MCP tool by Spring AI auto-configuration. All methods:
 * <ul>
 *   <li>Read tenant ID from {@link TenantContextHolder} (never from request parameters).</li>
 *   <li>Delegate to {@link TicketServiceClient} for downstream calls.</li>
 *   <li>Return descriptive error messages (never throw to MCP caller).</li>
 *   <li>Log {@code tenantId} and entity IDs; never log PII.</li>
 * </ul>
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class TicketTools {

    private final TicketServiceClient ticketServiceClient;

    @Tool(description = "Create a new support ticket for a customer. Use this when a customer reports an issue that was not resolved by the FAQ knowledge base.")
    public CreateTicketResult createTicket(
            @ToolParam(description = "Brief title of the issue (max 200 characters)") String title,
            @ToolParam(description = "Detailed description of the customer's problem") String description,
            @ToolParam(description = "Category slug (e.g. ORDER_ISSUE, DELIVERY_PROBLEM, PAYMENT_ISSUE)") String categorySlug,
            @ToolParam(description = "Customer ID (UUID)") String customerId,
            @ToolParam(description = "Order ID if the issue is order-related; pass null or empty string if not applicable") String orderId) {

        String tenantId = TenantContextHolder.getTenantId();
        log.info("TicketTools.createTicket: tenantId={}, customerId={}, categorySlug={}", tenantId, customerId, categorySlug);

        try {
            String effectiveOrderId = (orderId == null || orderId.isBlank()) ? null : orderId;
            return ticketServiceClient.createTicket(tenantId, customerId, title, description, categorySlug, effectiveOrderId);
        } catch (McpToolException ex) {
            log.warn("TicketTools.createTicket failed: tenantId={}, customerId={}, error={}",
                    tenantId, customerId, ex.getMessage());
            return new CreateTicketResult(null, "ERROR", ex.getMessage());
        } catch (Exception ex) {
            log.error("TicketTools.createTicket unexpected error: tenantId={}, customerId={}, error={}",
                    tenantId, customerId, ex.getMessage(), ex);
            return new CreateTicketResult(null, "ERROR",
                    "An unexpected error occurred while creating your ticket. Please try again later.");
        }
    }

    @Tool(description = "Get details of an existing ticket by its ticket number (e.g. FC-2024-001234). Use this to check ticket status or recent activity.")
    public TicketDetailResult getTicket(
            @ToolParam(description = "The ticket number in format PREFIX-YEAR-SEQUENCE (e.g. FC-2024-001234)") String ticketNumber) {

        String tenantId = TenantContextHolder.getTenantId();
        log.info("TicketTools.getTicket: tenantId={}, ticketNumber={}", tenantId, ticketNumber);

        try {
            return ticketServiceClient.getTicket(tenantId, ticketNumber);
        } catch (McpToolException ex) {
            log.warn("TicketTools.getTicket failed: tenantId={}, ticketNumber={}, error={}",
                    tenantId, ticketNumber, ex.getMessage());
            return new TicketDetailResult(ticketNumber, "ERROR", ex.getMessage(), null, null, Instant.now(), null);
        } catch (Exception ex) {
            log.error("TicketTools.getTicket unexpected error: tenantId={}, ticketNumber={}, error={}",
                    tenantId, ticketNumber, ex.getMessage(), ex);
            return new TicketDetailResult(ticketNumber, "ERROR",
                    "An unexpected error occurred while retrieving ticket details. Please try again later.",
                    null, null, Instant.now(), null);
        }
    }

    @Tool(description = "List recent tickets for a customer. Use this to show a customer their ticket history or check existing open issues.")
    public ListTicketsResult listTickets(
            @ToolParam(description = "Customer ID (UUID)") String customerId,
            @ToolParam(description = "Maximum number of tickets to return (1-10)") int limit) {

        String tenantId = TenantContextHolder.getTenantId();
        log.info("TicketTools.listTickets: tenantId={}, customerId={}, limit={}", tenantId, customerId, limit);

        try {
            return ticketServiceClient.listTickets(tenantId, customerId, limit);
        } catch (McpToolException ex) {
            log.warn("TicketTools.listTickets failed: tenantId={}, customerId={}, error={}",
                    tenantId, customerId, ex.getMessage());
            return new ListTicketsResult(List.of(), 0);
        } catch (Exception ex) {
            log.error("TicketTools.listTickets unexpected error: tenantId={}, customerId={}, error={}",
                    tenantId, customerId, ex.getMessage(), ex);
            return new ListTicketsResult(List.of(), 0);
        }
    }
}
