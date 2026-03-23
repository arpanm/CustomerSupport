package in.supporthub.customer.service;

import in.supporthub.customer.dto.OrderSummary;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Fetches order history from the order-sync-service via a reactive WebClient call.
 *
 * <p>Defensive design: any exception (network, timeout, 4xx, 5xx, JSON parse) is caught
 * and results in an empty list. The order history feature must NEVER cause the customer
 * profile page to fail.
 *
 * <p>The {@code X-Tenant-ID} header is forwarded so that the order-sync-service can apply
 * its own tenant isolation.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class OrderHistoryService {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(3);
    private static final String TENANT_ID_HEADER = "X-Tenant-ID";

    private final WebClient.Builder webClientBuilder;

    @Value("${order-sync.base-url:http://order-sync-service:8089}")
    private String orderSyncBaseUrl;

    /**
     * Retrieves the order history for a customer from the order-sync-service.
     *
     * <p>Returns an empty list if the order-sync-service is unavailable or returns an error.
     * This method is intentionally defensive — it never throws.
     *
     * @param tenantId   tenant UUID (forwarded as a header)
     * @param customerId the customer whose orders to retrieve
     * @return list of order summaries, or empty list on any failure
     */
    public List<OrderSummary> getOrderHistory(UUID tenantId, UUID customerId) {
        log.debug("Fetching order history: tenantId={}, customerId={}", tenantId, customerId);

        try {
            List<OrderSummary> orders = webClientBuilder
                    .baseUrl(orderSyncBaseUrl)
                    .build()
                    .get()
                    .uri("/api/v1/internal/orders/customer/{customerId}", customerId)
                    .header(TENANT_ID_HEADER, tenantId.toString())
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<List<OrderSummary>>() {})
                    .timeout(REQUEST_TIMEOUT)
                    .block();

            if (orders == null) {
                log.warn("Order-sync-service returned null body: tenantId={}, customerId={}", tenantId, customerId);
                return Collections.emptyList();
            }

            log.debug("Order history fetched: tenantId={}, customerId={}, count={}", tenantId, customerId, orders.size());
            return orders;

        } catch (Exception ex) {
            // Defensive: never let order-sync unavailability propagate to the caller
            log.warn("Failed to fetch order history (returning empty list): tenantId={}, customerId={}, error={}",
                    tenantId, customerId, ex.getMessage());
            return Collections.emptyList();
        }
    }
}
