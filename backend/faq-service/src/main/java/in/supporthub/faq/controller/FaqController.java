package in.supporthub.faq.controller;

import in.supporthub.faq.dto.CreateFaqRequest;
import in.supporthub.faq.dto.FaqResponse;
import in.supporthub.faq.dto.FaqSearchRequest;
import in.supporthub.faq.dto.FaqSearchResult;
import in.supporthub.faq.dto.UpdateFaqRequest;
import in.supporthub.faq.service.FaqService;
import in.supporthub.faq.service.SemanticSearchService;
import in.supporthub.shared.security.TenantContextHolder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for FAQ CRUD and semantic search.
 *
 * <p>Public endpoints (no authentication required):
 * <ul>
 *   <li>{@code GET  /api/v1/faqs} — list published FAQs for a tenant</li>
 *   <li>{@code GET  /api/v1/faqs/{id}} — get a single FAQ (increments view count)</li>
 *   <li>{@code POST /api/v1/faqs/search} — semantic/keyword FAQ search</li>
 * </ul>
 *
 * <p>Admin-only endpoints (require role check via {@code X-User-Role} header):
 * <ul>
 *   <li>{@code POST   /api/v1/faqs} — create FAQ</li>
 *   <li>{@code PUT    /api/v1/faqs/{id}} — update FAQ</li>
 *   <li>{@code DELETE /api/v1/faqs/{id}} — delete FAQ</li>
 *   <li>{@code POST   /api/v1/faqs/{id}/publish} — publish FAQ</li>
 * </ul>
 *
 * <p>Role enforcement: the API gateway validates the JWT and injects the {@code X-User-Role}
 * header. This service performs a secondary role check to enforce defence-in-depth.
 */
@RestController
@RequestMapping("/api/v1/faqs")
@Slf4j
@RequiredArgsConstructor
@Tag(name = "FAQ", description = "FAQ content management and self-resolution search")
public class FaqController {

    private static final String ROLE_HEADER = "X-User-Role";
    private static final String ADMIN_ROLE = "ADMIN";

    private final FaqService faqService;
    private final SemanticSearchService semanticSearchService;

    // =========================================================================
    // Public endpoints
    // =========================================================================

    @GetMapping
    @Operation(summary = "List published FAQs for the current tenant")
    public ResponseEntity<Page<FaqResponse>> listPublishedFaqs(
            @RequestHeader(value = "X-Tenant-ID") String tenantIdStr,
            @RequestParam(required = false) UUID categoryId,
            @PageableDefault(size = 20) Pageable pageable) {

        UUID tenantId = UUID.fromString(tenantIdStr);
        log.info("List FAQs: tenantId={}, categoryId={}", tenantId, categoryId);
        Page<FaqResponse> page = faqService.listFaqs(tenantId, categoryId, pageable);
        return ResponseEntity.ok(page);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a single FAQ entry by ID (increments view count)")
    public ResponseEntity<FaqResponse> getFaq(
            @RequestHeader(value = "X-Tenant-ID") String tenantIdStr,
            @PathVariable UUID id) {

        UUID tenantId = UUID.fromString(tenantIdStr);
        log.info("Get FAQ: id={}, tenantId={}", id, tenantId);
        FaqResponse response = faqService.getFaq(tenantId, id);
        faqService.incrementViewCount(tenantId, id);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/search")
    @Operation(summary = "Semantic/keyword FAQ search for customer self-resolution")
    public ResponseEntity<List<FaqSearchResult>> search(
            @RequestHeader(value = "X-Tenant-ID") String tenantIdStr,
            @Valid @RequestBody FaqSearchRequest request) {

        UUID tenantId = UUID.fromString(tenantIdStr);
        log.info("FAQ search: tenantId={}, limit={}", tenantId, request.limit());
        List<FaqSearchResult> results = semanticSearchService.search(tenantId, request.query(),
                request.limit());
        return ResponseEntity.ok(results);
    }

    // =========================================================================
    // Admin-only endpoints
    // =========================================================================

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a new FAQ entry (ADMIN only)")
    public ResponseEntity<FaqResponse> createFaq(
            @RequestHeader(value = "X-Tenant-ID") String tenantIdStr,
            @RequestHeader(value = ROLE_HEADER, required = false) String role,
            @Valid @RequestBody CreateFaqRequest request) {

        requireAdminRole(role);
        UUID tenantId = UUID.fromString(tenantIdStr);
        log.info("Create FAQ: tenantId={}", tenantId);
        FaqResponse response = faqService.createFaq(tenantId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update an existing FAQ entry (ADMIN only)")
    public ResponseEntity<FaqResponse> updateFaq(
            @RequestHeader(value = "X-Tenant-ID") String tenantIdStr,
            @RequestHeader(value = ROLE_HEADER, required = false) String role,
            @PathVariable UUID id,
            @RequestBody UpdateFaqRequest request) {

        requireAdminRole(role);
        UUID tenantId = UUID.fromString(tenantIdStr);
        log.info("Update FAQ: id={}, tenantId={}", id, tenantId);
        return ResponseEntity.ok(faqService.updateFaq(tenantId, id, request));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a FAQ entry (ADMIN only)")
    public ResponseEntity<Void> deleteFaq(
            @RequestHeader(value = "X-Tenant-ID") String tenantIdStr,
            @RequestHeader(value = ROLE_HEADER, required = false) String role,
            @PathVariable UUID id) {

        requireAdminRole(role);
        UUID tenantId = UUID.fromString(tenantIdStr);
        log.info("Delete FAQ: id={}, tenantId={}", id, tenantId);
        faqService.deleteFaq(tenantId, id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/publish")
    @Operation(summary = "Publish a FAQ entry (ADMIN only)")
    public ResponseEntity<FaqResponse> publishFaq(
            @RequestHeader(value = "X-Tenant-ID") String tenantIdStr,
            @RequestHeader(value = ROLE_HEADER, required = false) String role,
            @PathVariable UUID id) {

        requireAdminRole(role);
        UUID tenantId = UUID.fromString(tenantIdStr);
        log.info("Publish FAQ: id={}, tenantId={}", id, tenantId);
        return ResponseEntity.ok(faqService.publishFaq(tenantId, id));
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    /**
     * Validates that the caller holds the ADMIN role.
     * Defence-in-depth: the API gateway has already validated the JWT.
     *
     * @throws org.springframework.security.access.AccessDeniedException if role is not ADMIN
     */
    private void requireAdminRole(String role) {
        if (!ADMIN_ROLE.equalsIgnoreCase(role)) {
            log.warn("Admin endpoint accessed without ADMIN role: role={}", role);
            throw new org.springframework.security.access.AccessDeniedException(
                    "ADMIN role required");
        }
    }
}
