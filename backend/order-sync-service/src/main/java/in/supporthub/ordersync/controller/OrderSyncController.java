package in.supporthub.ordersync.controller;

import in.supporthub.ordersync.dto.CustomerOrdersResponse;
import in.supporthub.ordersync.dto.OrderResponse;
import in.supporthub.ordersync.service.OmsClientService;
import in.supporthub.shared.dto.ApiResponse;
import in.supporthub.shared.security.TenantContextHolder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Internal REST API exposing order data to other SupportHub services.
 *
 * <p>These endpoints are consumed service-to-service (e.g., ticket-service fetching
 * a customer's order history for context enrichment). They require a valid JWT token
 * (enforced by {@link in.supporthub.ordersync.config.SecurityConfig}).
 *
 * <p>All requests must carry:
 * <ul>
 *   <li>{@code X-Tenant-ID} — set by the API gateway after JWT validation</li>
 *   <li>{@code X-User-Id} — authenticated user UUID</li>
 *   <li>{@code Authorization: Bearer {token}} — valid JWT</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/internal/orders")
@Tag(name = "Internal Order API", description = "Internal order data access — service-to-service only")
@Slf4j
@RequiredArgsConstructor
public class OrderSyncController {

    static final String HEADER_TENANT_ID = "X-Tenant-ID";
    static final String HEADER_USER_ID = "X-User-Id";

    private final OmsClientService omsClientService;

    /**
     * Returns a customer's recent orders from the tenant's OMS.
     *
     * @param customerId the customer identifier as known to the OMS
     * @param limit      maximum number of orders to return (default 10)
     * @return wrapped list of orders; empty list if no OMS config or on OMS error
     */
    @GetMapping("/customer/{customerId}")
    @Operation(summary = "Get customer orders from OMS (internal)")
    public ResponseEntity<ApiResponse<CustomerOrdersResponse>> getCustomerOrders(
            @PathVariable String customerId,
            @RequestParam(defaultValue = "10") int limit) {

        UUID tenantId = UUID.fromString(TenantContextHolder.getTenantId());

        log.info("Fetching customer orders: tenantId={}, limit={}", tenantId, limit);

        List<OrderResponse> orders = omsClientService.fetchCustomerOrders(tenantId, customerId, limit);
        CustomerOrdersResponse response = CustomerOrdersResponse.of(orders);

        return ResponseEntity.ok(ApiResponse.of(response));
    }

    /**
     * Returns a single order by its OMS order ID.
     *
     * @param orderId the OMS order identifier
     * @return the order if found, otherwise 404
     */
    @GetMapping("/{orderId}")
    @Operation(summary = "Get a single order by OMS order ID (internal)")
    public ResponseEntity<ApiResponse<OrderResponse>> getOrder(@PathVariable String orderId) {

        UUID tenantId = UUID.fromString(TenantContextHolder.getTenantId());

        log.info("Fetching single order: tenantId={}, orderId={}", tenantId, orderId);

        Optional<OrderResponse> order = omsClientService.fetchOrderById(tenantId, orderId);

        return order
                .map(o -> ResponseEntity.ok(ApiResponse.of(o)))
                .orElse(ResponseEntity.notFound().build());
    }
}
