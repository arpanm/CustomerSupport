package in.supporthub.mcp.client;

import in.supporthub.mcp.dto.CreateTicketResult;
import in.supporthub.mcp.dto.ListTicketsResult;
import in.supporthub.mcp.dto.TicketDetailResult;
import in.supporthub.mcp.dto.TicketSummary;
import in.supporthub.mcp.exception.McpToolException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * REST client for communicating with the {@code ticket-service} (port 8082).
 *
 * <p>All calls include {@code X-Tenant-ID} and {@code X-User-Id} headers so that
 * the downstream service can perform tenant isolation. Calls time out after 10 s.
 * On error a {@link McpToolException} is thrown with a user-friendly message.
 */
@Component
@Slf4j
public class TicketServiceClient {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final WebClient webClient;

    public TicketServiceClient(
            WebClient.Builder webClientBuilder,
            @Value("${ticket-service.base-url:http://ticket-service:8082}") String baseUrl) {
        this.webClient = webClientBuilder
                .baseUrl(baseUrl)
                .build();
    }

    /**
     * Creates a new ticket on behalf of a customer.
     *
     * @param tenantId     UUID of the tenant
     * @param customerId   UUID of the customer raising the ticket
     * @param title        brief title of the issue
     * @param description  detailed description of the customer's problem
     * @param categorySlug category slug (e.g. ORDER_ISSUE)
     * @param orderId      order ID if order-related, may be {@code null}
     * @return the creation result containing the new ticket number and status
     * @throws McpToolException if the downstream call fails
     */
    public CreateTicketResult createTicket(
            String tenantId,
            String customerId,
            String title,
            String description,
            String categorySlug,
            String orderId) {

        log.info("TicketServiceClient.createTicket: tenantId={}, customerId={}, categorySlug={}",
                tenantId, customerId, categorySlug);

        try {
            Map<String, Object> body = new java.util.LinkedHashMap<>();
            body.put("customerId", customerId);
            body.put("title", title);
            body.put("description", description);
            body.put("categorySlug", categorySlug);
            if (orderId != null && !orderId.isBlank()) {
                body.put("orderId", orderId);
            }

            return webClient.post()
                    .uri("/api/v1/tickets")
                    .header("X-Tenant-ID", tenantId)
                    .header("X-User-Id", customerId)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(TicketCreateResponse.class)
                    .timeout(TIMEOUT)
                    .map(resp -> new CreateTicketResult(
                            resp.ticketNumber(),
                            resp.status(),
                            "Your ticket " + resp.ticketNumber() + " has been created successfully."))
                    .block();

        } catch (WebClientResponseException ex) {
            log.warn("TicketServiceClient.createTicket failed: tenantId={}, customerId={}, status={}, body={}",
                    tenantId, customerId, ex.getStatusCode(), ex.getResponseBodyAsString());
            throw new McpToolException(
                    "Unable to create your ticket at this time. Please try again later. (HTTP " + ex.getStatusCode().value() + ")",
                    ex);
        } catch (Exception ex) {
            log.error("TicketServiceClient.createTicket error: tenantId={}, customerId={}, error={}",
                    tenantId, customerId, ex.getMessage(), ex);
            throw new McpToolException(
                    "Unable to create your ticket due to a service error. Please try again later.",
                    ex);
        }
    }

    /**
     * Retrieves full details of a ticket by its ticket number.
     *
     * @param tenantId     UUID of the tenant
     * @param ticketNumber the ticket number (e.g. FC-2024-001234)
     * @return ticket detail result
     * @throws McpToolException if the downstream call fails or the ticket is not found
     */
    public TicketDetailResult getTicket(String tenantId, String ticketNumber) {
        log.info("TicketServiceClient.getTicket: tenantId={}, ticketNumber={}", tenantId, ticketNumber);

        try {
            return webClient.get()
                    .uri("/api/v1/tickets/number/{ticketNumber}", ticketNumber)
                    .header("X-Tenant-ID", tenantId)
                    .retrieve()
                    .bodyToMono(TicketDetailResponse.class)
                    .timeout(TIMEOUT)
                    .map(resp -> new TicketDetailResult(
                            resp.ticketNumber(),
                            resp.status(),
                            resp.title(),
                            resp.categorySlug(),
                            resp.assignedAgentName(),
                            resp.createdAt(),
                            resp.lastActivityDescription()))
                    .block();

        } catch (WebClientResponseException.NotFound ex) {
            log.info("TicketServiceClient.getTicket not found: tenantId={}, ticketNumber={}", tenantId, ticketNumber);
            throw new McpToolException(
                    "Ticket " + ticketNumber + " was not found. Please check the ticket number and try again.");
        } catch (WebClientResponseException ex) {
            log.warn("TicketServiceClient.getTicket failed: tenantId={}, ticketNumber={}, status={}",
                    tenantId, ticketNumber, ex.getStatusCode());
            throw new McpToolException(
                    "Unable to retrieve ticket " + ticketNumber + " at this time. (HTTP " + ex.getStatusCode().value() + ")",
                    ex);
        } catch (Exception ex) {
            log.error("TicketServiceClient.getTicket error: tenantId={}, ticketNumber={}, error={}",
                    tenantId, ticketNumber, ex.getMessage(), ex);
            throw new McpToolException(
                    "Unable to retrieve ticket details due to a service error. Please try again later.",
                    ex);
        }
    }

    /**
     * Lists recent tickets for a customer.
     *
     * @param tenantId   UUID of the tenant
     * @param customerId UUID of the customer
     * @param limit      maximum number of tickets to return (1–10)
     * @return list tickets result
     * @throws McpToolException if the downstream call fails
     */
    public ListTicketsResult listTickets(String tenantId, String customerId, int limit) {
        int safeLimit = Math.max(1, Math.min(10, limit));
        log.info("TicketServiceClient.listTickets: tenantId={}, customerId={}, limit={}", tenantId, customerId, safeLimit);

        try {
            return webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/v1/tickets")
                            .queryParam("customerId", customerId)
                            .queryParam("size", safeLimit)
                            .queryParam("sort", "createdAt,desc")
                            .build())
                    .header("X-Tenant-ID", tenantId)
                    .header("X-User-Id", customerId)
                    .retrieve()
                    .bodyToMono(TicketListResponse.class)
                    .timeout(TIMEOUT)
                    .map(resp -> {
                        List<TicketSummary> summaries = resp.content() == null
                                ? List.of()
                                : resp.content().stream()
                                        .map(t -> new TicketSummary(
                                                t.ticketNumber(),
                                                t.status(),
                                                t.title(),
                                                t.createdAt()))
                                        .toList();
                        return new ListTicketsResult(summaries, summaries.size());
                    })
                    .block();

        } catch (WebClientResponseException ex) {
            log.warn("TicketServiceClient.listTickets failed: tenantId={}, customerId={}, status={}",
                    tenantId, customerId, ex.getStatusCode());
            throw new McpToolException(
                    "Unable to retrieve tickets at this time. (HTTP " + ex.getStatusCode().value() + ")",
                    ex);
        } catch (Exception ex) {
            log.error("TicketServiceClient.listTickets error: tenantId={}, customerId={}, error={}",
                    tenantId, customerId, ex.getMessage(), ex);
            throw new McpToolException(
                    "Unable to list tickets due to a service error. Please try again later.",
                    ex);
        }
    }

    // -----------------------------------------------------------------------
    // Internal response DTOs — mirrors ticket-service API contracts
    // -----------------------------------------------------------------------

    private record TicketCreateResponse(String ticketNumber, String status) {}

    private record TicketDetailResponse(
            String ticketNumber,
            String status,
            String title,
            String categorySlug,
            String assignedAgentName,
            java.time.Instant createdAt,
            String lastActivityDescription) {}

    private record TicketListResponse(List<TicketItem> content) {}

    private record TicketItem(
            String ticketNumber,
            String status,
            String title,
            java.time.Instant createdAt) {}
}
