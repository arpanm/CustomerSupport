package in.supporthub.customer.controller;

import in.supporthub.customer.dto.CreateAddressRequest;
import in.supporthub.customer.dto.CustomerAddressResponse;
import in.supporthub.customer.dto.CustomerProfileResponse;
import in.supporthub.customer.dto.OrderSummary;
import in.supporthub.customer.dto.UpdateProfileRequest;
import in.supporthub.customer.service.CustomerService;
import in.supporthub.customer.service.OrderHistoryService;
import in.supporthub.shared.dto.ApiResponse;
import in.supporthub.shared.security.TenantContextHolder;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for customer self-service operations.
 *
 * <p>All endpoints operate on the authenticated customer's own data only.
 * The customer's UUID is read from the {@code X-User-Id} header injected by the API gateway
 * after JWT validation — it is never accepted as a user-controlled path/query parameter.
 *
 * <p>Tenant context is populated by {@link TenantContextFilter} before any handler runs.
 *
 * <p>Logging: always log tenantId and customerId. NEVER log phone, email, or display name (PII).
 */
@RestController
@RequestMapping("/api/v1/customers")
@Slf4j
@RequiredArgsConstructor
public class CustomerController {

    /** Header injected by the API gateway carrying the authenticated user's UUID. */
    private static final String USER_ID_HEADER = "X-User-Id";

    private final CustomerService customerService;
    private final OrderHistoryService orderHistoryService;

    // -------------------------------------------------------------------------
    // Profile
    // -------------------------------------------------------------------------

    /**
     * Returns the authenticated customer's profile.
     *
     * <p>GET /api/v1/customers/me
     */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<CustomerProfileResponse>> getProfile(
            @RequestHeader(USER_ID_HEADER) UUID customerId) {

        UUID tenantId = UUID.fromString(TenantContextHolder.getTenantId());
        log.debug("GET /me: tenantId={}, customerId={}", tenantId, customerId);

        CustomerProfileResponse profile = customerService.getProfile(tenantId, customerId);
        return ResponseEntity.ok(ApiResponse.of(profile));
    }

    /**
     * Updates the authenticated customer's mutable profile fields.
     *
     * <p>Phone is immutable. Only name, language, and timezone may change.
     *
     * <p>PUT /api/v1/customers/me
     */
    @PutMapping("/me")
    public ResponseEntity<ApiResponse<CustomerProfileResponse>> updateProfile(
            @RequestHeader(USER_ID_HEADER) UUID customerId,
            @Valid @RequestBody UpdateProfileRequest request) {

        UUID tenantId = UUID.fromString(TenantContextHolder.getTenantId());
        log.info("PUT /me: tenantId={}, customerId={}", tenantId, customerId);

        CustomerProfileResponse updated = customerService.updateProfile(tenantId, customerId, request);
        return ResponseEntity.ok(ApiResponse.of(updated));
    }

    // -------------------------------------------------------------------------
    // Order history
    // -------------------------------------------------------------------------

    /**
     * Returns the authenticated customer's order history from the order-sync-service.
     *
     * <p>Defensive: returns an empty list if the order-sync-service is unavailable.
     *
     * <p>GET /api/v1/customers/me/orders
     */
    @GetMapping("/me/orders")
    public ResponseEntity<ApiResponse<List<OrderSummary>>> getOrderHistory(
            @RequestHeader(USER_ID_HEADER) UUID customerId) {

        UUID tenantId = UUID.fromString(TenantContextHolder.getTenantId());
        log.debug("GET /me/orders: tenantId={}, customerId={}", tenantId, customerId);

        List<OrderSummary> orders = orderHistoryService.getOrderHistory(tenantId, customerId);
        return ResponseEntity.ok(ApiResponse.of(orders));
    }

    // -------------------------------------------------------------------------
    // Addresses
    // -------------------------------------------------------------------------

    /**
     * Returns all saved addresses for the authenticated customer.
     *
     * <p>GET /api/v1/customers/me/addresses
     */
    @GetMapping("/me/addresses")
    public ResponseEntity<ApiResponse<List<CustomerAddressResponse>>> getAddresses(
            @RequestHeader(USER_ID_HEADER) UUID customerId) {

        UUID tenantId = UUID.fromString(TenantContextHolder.getTenantId());
        log.debug("GET /me/addresses: tenantId={}, customerId={}", tenantId, customerId);

        List<CustomerAddressResponse> addresses = customerService.getAddresses(tenantId, customerId);
        return ResponseEntity.ok(ApiResponse.of(addresses));
    }

    /**
     * Creates a new address for the authenticated customer.
     *
     * <p>POST /api/v1/customers/me/addresses
     */
    @PostMapping("/me/addresses")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<ApiResponse<CustomerAddressResponse>> addAddress(
            @RequestHeader(USER_ID_HEADER) UUID customerId,
            @Valid @RequestBody CreateAddressRequest request) {

        UUID tenantId = UUID.fromString(TenantContextHolder.getTenantId());
        log.info("POST /me/addresses: tenantId={}, customerId={}", tenantId, customerId);

        CustomerAddressResponse created = customerService.addAddress(tenantId, customerId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(created));
    }

    /**
     * Replaces all fields of an existing address.
     *
     * <p>PUT /api/v1/customers/me/addresses/{addressId}
     */
    @PutMapping("/me/addresses/{addressId}")
    public ResponseEntity<ApiResponse<CustomerAddressResponse>> updateAddress(
            @RequestHeader(USER_ID_HEADER) UUID customerId,
            @PathVariable UUID addressId,
            @Valid @RequestBody CreateAddressRequest request) {

        UUID tenantId = UUID.fromString(TenantContextHolder.getTenantId());
        log.info("PUT /me/addresses/{}: tenantId={}, customerId={}", addressId, tenantId, customerId);

        CustomerAddressResponse updated = customerService.updateAddress(tenantId, customerId, addressId, request);
        return ResponseEntity.ok(ApiResponse.of(updated));
    }

    /**
     * Deletes an address.
     *
     * <p>DELETE /api/v1/customers/me/addresses/{addressId}
     */
    @DeleteMapping("/me/addresses/{addressId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ResponseEntity<Void> deleteAddress(
            @RequestHeader(USER_ID_HEADER) UUID customerId,
            @PathVariable UUID addressId) {

        UUID tenantId = UUID.fromString(TenantContextHolder.getTenantId());
        log.info("DELETE /me/addresses/{}: tenantId={}, customerId={}", addressId, tenantId, customerId);

        customerService.deleteAddress(tenantId, customerId, addressId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Sets the specified address as the customer's default.
     *
     * <p>POST /api/v1/customers/me/addresses/{addressId}/default
     */
    @PostMapping("/me/addresses/{addressId}/default")
    public ResponseEntity<ApiResponse<CustomerAddressResponse>> setDefaultAddress(
            @RequestHeader(USER_ID_HEADER) UUID customerId,
            @PathVariable UUID addressId) {

        UUID tenantId = UUID.fromString(TenantContextHolder.getTenantId());
        log.info("POST /me/addresses/{}/default: tenantId={}, customerId={}", addressId, tenantId, customerId);

        CustomerAddressResponse updated = customerService.setDefaultAddress(tenantId, customerId, addressId);
        return ResponseEntity.ok(ApiResponse.of(updated));
    }
}
