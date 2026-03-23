package in.supporthub.ordersync.controller;

import in.supporthub.ordersync.domain.OmsConfig;
import in.supporthub.ordersync.dto.OmsConfigRequest;
import in.supporthub.ordersync.service.OmsConfigService;
import in.supporthub.shared.dto.ApiResponse;
import in.supporthub.shared.security.TenantContextHolder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * Admin REST API for managing per-tenant OMS configurations.
 *
 * <p>All endpoints require:
 * <ul>
 *   <li>{@code X-Tenant-ID} — tenant UUID, injected by the API gateway after JWT validation</li>
 *   <li>{@code X-User-Role} — must be {@code ADMIN} or {@code SUPER_ADMIN}</li>
 *   <li>{@code Authorization: Bearer {token}} — valid JWT</li>
 * </ul>
 *
 * <p>The API key is never returned in GET responses — only masked metadata is exposed.
 */
@RestController
@RequestMapping("/api/v1/admin/oms-config")
@Tag(name = "OMS Config Admin API", description = "Manage tenant OMS integration configuration")
@Slf4j
@RequiredArgsConstructor
public class OmsConfigController {

    static final String HEADER_USER_ROLE = "X-User-Role";

    private final OmsConfigService omsConfigService;

    /**
     * Creates or updates the OMS configuration for the requesting tenant.
     *
     * @param userRole role header — must be ADMIN or SUPER_ADMIN
     * @param request  OMS config details (API key is plaintext, encrypted before storage)
     * @return 201 Created on success
     */
    @PostMapping
    @Operation(summary = "Save (create or replace) OMS config for tenant (ADMIN only)")
    public ResponseEntity<ApiResponse<Map<String, String>>> saveConfig(
            @RequestHeader(HEADER_USER_ROLE) String userRole,
            @Valid @RequestBody OmsConfigRequest request) {

        requireAdminRole(userRole);

        UUID tenantId = UUID.fromString(TenantContextHolder.getTenantId());
        omsConfigService.saveConfig(tenantId, request);

        log.info("OMS config saved via API: tenantId={}", tenantId);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.of(Map.of("status", "saved")));
    }

    /**
     * Returns the OMS configuration for the requesting tenant.
     *
     * <p>The API key is NEVER returned — only safe metadata is included.
     *
     * @param userRole role header — must be ADMIN or SUPER_ADMIN
     * @return OMS config metadata (URL, auth type, active status)
     */
    @GetMapping
    @Operation(summary = "Get OMS config for tenant (ADMIN only) — API key is never returned")
    public ResponseEntity<ApiResponse<OmsConfigView>> getConfig(
            @RequestHeader(HEADER_USER_ROLE) String userRole) {

        requireAdminRole(userRole);

        UUID tenantId = UUID.fromString(TenantContextHolder.getTenantId());
        OmsConfig config = omsConfigService.getConfig(tenantId);

        OmsConfigView view = new OmsConfigView(
                config.getId(),
                config.getOmsBaseUrl(),
                config.getAuthType(),
                config.getHeaderName(),
                config.isActive(),
                config.getCreatedAt(),
                config.getUpdatedAt()
        );

        return ResponseEntity.ok(ApiResponse.of(view));
    }

    /**
     * Deletes the OMS configuration for the requesting tenant.
     *
     * @param userRole role header — must be ADMIN or SUPER_ADMIN
     * @return 204 No Content
     */
    @DeleteMapping
    @Operation(summary = "Delete OMS config for tenant (ADMIN only)")
    public ResponseEntity<Void> deleteConfig(
            @RequestHeader(HEADER_USER_ROLE) String userRole) {

        requireAdminRole(userRole);

        UUID tenantId = UUID.fromString(TenantContextHolder.getTenantId());
        omsConfigService.deleteConfig(tenantId);

        log.info("OMS config deleted via API: tenantId={}", tenantId);

        return ResponseEntity.noContent().build();
    }

    // =========================================================================
    // Private helpers & inner types
    // =========================================================================

    private void requireAdminRole(String userRole) {
        if (userRole == null
                || (!userRole.equalsIgnoreCase("ADMIN")
                    && !userRole.equalsIgnoreCase("SUPER_ADMIN"))) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Only ADMIN or SUPER_ADMIN may manage OMS configuration");
        }
    }

    /**
     * Safe view of OMS config — never exposes the encrypted API key bytes.
     */
    public record OmsConfigView(
            java.util.UUID id,
            String omsBaseUrl,
            String authType,
            String headerName,
            boolean isActive,
            java.time.Instant createdAt,
            java.time.Instant updatedAt
    ) {
    }
}
