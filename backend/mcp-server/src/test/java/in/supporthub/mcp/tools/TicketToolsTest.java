package in.supporthub.mcp.tools;

import in.supporthub.mcp.client.TicketServiceClient;
import in.supporthub.mcp.dto.CreateTicketResult;
import in.supporthub.mcp.dto.ListTicketsResult;
import in.supporthub.mcp.dto.TicketDetailResult;
import in.supporthub.mcp.dto.TicketSummary;
import in.supporthub.mcp.exception.McpToolException;
import in.supporthub.shared.security.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link TicketTools} MCP tool methods.
 *
 * <p>Tests verify:
 * <ul>
 *   <li>{@code createTicket} success: returns populated {@link CreateTicketResult}.</li>
 *   <li>{@code createTicket} service error: {@link McpToolException} is caught and
 *       returns an error result with status "ERROR".</li>
 *   <li>{@code getTicket} not-found: returns a descriptive error result.</li>
 *   <li>{@code listTickets} success: returns populated {@link ListTicketsResult}.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TicketTools — MCP tool unit tests")
class TicketToolsTest {

    private static final String TENANT_ID = "10000000-0000-0000-0000-000000000001";
    private static final String CUSTOMER_ID = "20000000-0000-0000-0000-000000000002";
    private static final String TICKET_NUMBER = "FC-2024-001234";

    @Mock
    private TicketServiceClient ticketServiceClient;

    @InjectMocks
    private TicketTools ticketTools;

    @BeforeEach
    void setTenantContext() {
        TenantContextHolder.setTenantId(TENANT_ID);
    }

    @AfterEach
    void clearTenantContext() {
        TenantContextHolder.clear();
    }

    // -----------------------------------------------------------------------
    // createTicket
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("createTicket — success returns populated CreateTicketResult")
    void createTicket_success_returnsTicketNumber() {
        // given
        CreateTicketResult expected = new CreateTicketResult(
                TICKET_NUMBER, "OPEN", "Your ticket " + TICKET_NUMBER + " has been created successfully.");

        when(ticketServiceClient.createTicket(
                eq(TENANT_ID), eq(CUSTOMER_ID),
                eq("Order not delivered"), eq("My order #12345 has not arrived."),
                eq("DELIVERY_PROBLEM"), isNull()))
                .thenReturn(expected);

        // when
        CreateTicketResult result = ticketTools.createTicket(
                "Order not delivered",
                "My order #12345 has not arrived.",
                "DELIVERY_PROBLEM",
                CUSTOMER_ID,
                null);

        // then
        assertThat(result).isNotNull();
        assertThat(result.ticketNumber()).isEqualTo(TICKET_NUMBER);
        assertThat(result.status()).isEqualTo("OPEN");
        assertThat(result.message()).contains(TICKET_NUMBER);

        verify(ticketServiceClient).createTicket(
                TENANT_ID, CUSTOMER_ID, "Order not delivered",
                "My order #12345 has not arrived.", "DELIVERY_PROBLEM", null);
    }

    @Test
    @DisplayName("createTicket — service error returns ERROR status with user-friendly message")
    void createTicket_serviceError_returnsErrorResult() {
        // given
        when(ticketServiceClient.createTicket(any(), any(), any(), any(), any(), any()))
                .thenThrow(new McpToolException("Unable to create your ticket at this time. Please try again later. (HTTP 503)"));

        // when
        CreateTicketResult result = ticketTools.createTicket(
                "Payment failed",
                "My payment was deducted but the order was not placed.",
                "PAYMENT_ISSUE",
                CUSTOMER_ID,
                "ORD-999");

        // then
        assertThat(result).isNotNull();
        assertThat(result.ticketNumber()).isNull();
        assertThat(result.status()).isEqualTo("ERROR");
        assertThat(result.message()).contains("Unable to create your ticket");
    }

    @Test
    @DisplayName("createTicket — unexpected exception returns generic error result")
    void createTicket_unexpectedException_returnsGenericError() {
        // given
        when(ticketServiceClient.createTicket(any(), any(), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("Connection refused"));

        // when
        CreateTicketResult result = ticketTools.createTicket(
                "Issue title", "Issue description", "ORDER_ISSUE", CUSTOMER_ID, null);

        // then
        assertThat(result.status()).isEqualTo("ERROR");
        assertThat(result.message()).contains("unexpected error");
    }

    // -----------------------------------------------------------------------
    // getTicket
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("getTicket — success returns populated TicketDetailResult")
    void getTicket_success_returnsDetails() {
        // given
        TicketDetailResult expected = new TicketDetailResult(
                TICKET_NUMBER, "IN_PROGRESS", "Order not delivered",
                "DELIVERY_PROBLEM", "Agent Smith", Instant.parse("2024-06-01T10:00:00Z"),
                "Agent assigned and investigating");

        when(ticketServiceClient.getTicket(TENANT_ID, TICKET_NUMBER)).thenReturn(expected);

        // when
        TicketDetailResult result = ticketTools.getTicket(TICKET_NUMBER);

        // then
        assertThat(result.ticketNumber()).isEqualTo(TICKET_NUMBER);
        assertThat(result.status()).isEqualTo("IN_PROGRESS");
        assertThat(result.assignedAgent()).isEqualTo("Agent Smith");
        assertThat(result.lastActivity()).isEqualTo("Agent assigned and investigating");
    }

    @Test
    @DisplayName("getTicket — ticket not found returns descriptive error result")
    void getTicket_notFound_returnsErrorResult() {
        // given
        when(ticketServiceClient.getTicket(TENANT_ID, "FC-2024-999999"))
                .thenThrow(new McpToolException(
                        "Ticket FC-2024-999999 was not found. Please check the ticket number and try again."));

        // when
        TicketDetailResult result = ticketTools.getTicket("FC-2024-999999");

        // then
        assertThat(result.ticketNumber()).isEqualTo("FC-2024-999999");
        assertThat(result.status()).isEqualTo("ERROR");
        assertThat(result.title()).contains("not found");
    }

    @Test
    @DisplayName("getTicket — service error returns descriptive error result")
    void getTicket_serviceError_returnsErrorResult() {
        // given
        when(ticketServiceClient.getTicket(eq(TENANT_ID), anyString()))
                .thenThrow(new McpToolException("Unable to retrieve ticket FC-2024-001234 at this time. (HTTP 500)"));

        // when
        TicketDetailResult result = ticketTools.getTicket(TICKET_NUMBER);

        // then
        assertThat(result.status()).isEqualTo("ERROR");
        assertThat(result.title()).contains("Unable to retrieve");
    }

    // -----------------------------------------------------------------------
    // listTickets
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("listTickets — success returns ticket summaries")
    void listTickets_success_returnsSummaries() {
        // given
        List<TicketSummary> summaries = List.of(
                new TicketSummary(TICKET_NUMBER, "OPEN", "Order not delivered", Instant.now()),
                new TicketSummary("FC-2024-001100", "RESOLVED", "Payment issue", Instant.now().minusSeconds(86400))
        );
        ListTicketsResult expected = new ListTicketsResult(summaries, 2);

        when(ticketServiceClient.listTickets(TENANT_ID, CUSTOMER_ID, 5)).thenReturn(expected);

        // when
        ListTicketsResult result = ticketTools.listTickets(CUSTOMER_ID, 5);

        // then
        assertThat(result.total()).isEqualTo(2);
        assertThat(result.tickets()).hasSize(2);
        assertThat(result.tickets().get(0).ticketNumber()).isEqualTo(TICKET_NUMBER);
    }

    @Test
    @DisplayName("listTickets — service error returns empty result")
    void listTickets_serviceError_returnsEmpty() {
        // given
        when(ticketServiceClient.listTickets(any(), any(), anyInt()))
                .thenThrow(new McpToolException("Unable to retrieve tickets at this time. (HTTP 503)"));

        // when
        ListTicketsResult result = ticketTools.listTickets(CUSTOMER_ID, 5);

        // then
        assertThat(result.total()).isZero();
        assertThat(result.tickets()).isEmpty();
    }
}
