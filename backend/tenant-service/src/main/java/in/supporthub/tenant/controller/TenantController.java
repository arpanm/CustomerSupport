package in.supporthub.tenant.controller;

import in.supporthub.tenant.dto.CreateTenantRequest;
import in.supporthub.tenant.dto.TenantConfigResponse;
import in.supporthub.tenant.dto.TenantResponse;
import in.supporthub.tenant.dto.UpdateTenantConfigRequest;
import in.supporthub.tenant.dto.UpdateTenantStatusRequest;
import in.supporthub.tenant.service.TenantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * REST controller for tenant management endpoints.
 *
 * <p>Route structure:
 * <ul>
 *   <li>{@code POST  /api/v1/admin/tenants}           — ROLE_SUPER_ADMIN: create tenant</li>
 *   <li>{@code GET   /api/v1/tenants/{slug}}           — public: resolve tenant by slug (used by gateway)</li>
 *   <li>{@code GET   /api/v1/admin/tenants/{id}}       — ROLE_ADMIN or ROLE_SUPER_ADMIN: get tenant by ID</li>
 *   <li>{@code PUT   /api/v1/admin/tenants/{id}/config}— ROLE_ADMIN or ROLE_SUPER_ADMIN: upsert config</li>
 *   <li>{@code GET   /api/v1/admin/tenants/{id}/config}— ROLE_ADMIN or ROLE_SUPER_ADMIN: get config</li>
 *   <li>{@code PATCH /api/v1/admin/tenants/{id}/status}— ROLE_SUPER_ADMIN: change status</li>
 * </ul>
 *
 * <p>Role enforcement is handled by {@link in.supporthub.tenant.config.SecurityConfig}.
 * This controller only validates input and delegates to {@link TenantService}.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Tenants", description = "Tenant lifecycle management")
public class TenantController {

    private final TenantService tenantService;

    /**
     * Creates a new tenant. Accessible by ROLE_SUPER_ADMIN only.
     *
     * @param req validated creation request
     * @return 201 Created with the new tenant response
     */
    @PostMapping("/admin/tenants")
    @Operation(summary = "Create a new tenant (SUPER_ADMIN only)")
    public ResponseEntity<TenantResponse> createTenant(@Valid @RequestBody CreateTenantRequest req) {
        TenantResponse response = tenantService.createTenant(req);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Resolves tenant information by slug. Public endpoint — used by the API gateway
     * to look up tenant context for incoming requests.
     *
     * @param slug the URL-friendly tenant slug
     * @return 200 OK with the tenant response
     */
    @GetMapping("/tenants/{slug}")
    @Operation(summary = "Get tenant by slug (public, used by gateway)")
    public ResponseEntity<TenantResponse> getBySlug(@PathVariable String slug) {
        return ResponseEntity.ok(tenantService.getBySlug(slug));
    }

    /**
     * Returns full tenant details by UUID. Requires ROLE_ADMIN or ROLE_SUPER_ADMIN.
     *
     * @param id the tenant UUID
     * @return 200 OK with the tenant response
     */
    @GetMapping("/admin/tenants/{id}")
    @Operation(summary = "Get tenant by ID (ADMIN or SUPER_ADMIN)")
    public ResponseEntity<TenantResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(tenantService.getById(id));
    }

    /**
     * Upserts configuration entries for the given tenant. Requires ROLE_ADMIN or ROLE_SUPER_ADMIN.
     *
     * @param id  the tenant UUID
     * @param req the request containing key-value config pairs
     * @return 200 OK with the updated config response
     */
    @PutMapping("/admin/tenants/{id}/config")
    @Operation(summary = "Upsert tenant config (ADMIN or SUPER_ADMIN)")
    public ResponseEntity<TenantConfigResponse> updateConfig(
            @PathVariable UUID id,
            @RequestBody UpdateTenantConfigRequest req) {
        return ResponseEntity.ok(tenantService.updateConfig(id, req));
    }

    /**
     * Returns all configuration entries for the given tenant. Requires ROLE_ADMIN or ROLE_SUPER_ADMIN.
     *
     * @param id the tenant UUID
     * @return 200 OK with the config response
     */
    @GetMapping("/admin/tenants/{id}/config")
    @Operation(summary = "Get tenant config (ADMIN or SUPER_ADMIN)")
    public ResponseEntity<TenantConfigResponse> getConfig(@PathVariable UUID id) {
        return ResponseEntity.ok(tenantService.getConfig(id));
    }

    /**
     * Updates the lifecycle status of a tenant. Requires ROLE_SUPER_ADMIN only.
     *
     * @param id  the tenant UUID
     * @param req the request containing the new status
     * @return 200 OK with the updated tenant response
     */
    @PatchMapping("/admin/tenants/{id}/status")
    @Operation(summary = "Update tenant status (SUPER_ADMIN only)")
    public ResponseEntity<TenantResponse> updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateTenantStatusRequest req) {
        return ResponseEntity.ok(tenantService.updateStatus(id, req));
    }
}
