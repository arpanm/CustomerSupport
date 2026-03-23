package in.supporthub.tenant.service;

import in.supporthub.tenant.domain.Tenant;
import in.supporthub.tenant.domain.TenantConfig;
import in.supporthub.tenant.domain.TenantStatus;
import in.supporthub.tenant.dto.CreateTenantRequest;
import in.supporthub.tenant.dto.TenantConfigResponse;
import in.supporthub.tenant.dto.TenantResponse;
import in.supporthub.tenant.dto.UpdateTenantConfigRequest;
import in.supporthub.tenant.dto.UpdateTenantStatusRequest;
import in.supporthub.tenant.event.TenantEventPublisher;
import in.supporthub.tenant.event.TenantOnboardedEvent;
import in.supporthub.tenant.exception.TenantNotFoundException;
import in.supporthub.tenant.exception.TenantSlugConflictException;
import in.supporthub.tenant.repository.TenantConfigRepository;
import in.supporthub.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Core business logic for tenant lifecycle management.
 *
 * <p>Responsibilities include:
 * <ul>
 *   <li>Provisioning new tenants with default configuration</li>
 *   <li>Resolving tenant identity by slug or ID</li>
 *   <li>Upserting per-tenant configuration entries</li>
 *   <li>Managing tenant lifecycle status transitions</li>
 * </ul>
 *
 * <p>Tenant IDs are never accepted from request parameters — all tenant context
 * is sourced from {@link in.supporthub.shared.security.TenantContextHolder} or
 * explicit path variables validated at the controller layer.
 */
@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class TenantService {

    private static final String DEFAULT_SLA_RESPONSE_HOURS_KEY = "sla.default_response_hours";
    private static final String DEFAULT_SLA_RESOLUTION_HOURS_KEY = "sla.default_resolution_hours";
    private static final String DEFAULT_BRANDING_PRIMARY_COLOR_KEY = "branding.primary_color";

    private final TenantRepository tenantRepository;
    private final TenantConfigRepository tenantConfigRepository;
    private final TenantEventPublisher tenantEventPublisher;

    /**
     * Creates a new tenant with the given request parameters.
     *
     * <p>Steps:
     * <ol>
     *   <li>Validates slug uniqueness — throws {@link TenantSlugConflictException} on collision.</li>
     *   <li>Persists the {@link Tenant} entity with status {@code PENDING}.</li>
     *   <li>Sets the self-referencing {@code tenantId} to the generated {@code id}.</li>
     *   <li>Seeds default configuration entries.</li>
     *   <li>Publishes a {@code tenant.onboarded} Kafka event.</li>
     * </ol>
     *
     * @param req validated creation request
     * @return response DTO for the newly created tenant
     * @throws TenantSlugConflictException if the slug is already taken
     */
    public TenantResponse createTenant(CreateTenantRequest req) {
        if (tenantRepository.existsBySlug(req.slug())) {
            throw new TenantSlugConflictException(req.slug());
        }

        Tenant tenant = Tenant.builder()
                .slug(req.slug())
                .name(req.name())
                .planType(req.planType())
                .status(TenantStatus.PENDING)
                .timezone(req.timezone() != null ? req.timezone() : "Asia/Kolkata")
                .locale(req.locale() != null ? req.locale() : "en-IN")
                .build();

        Tenant saved = tenantRepository.save(tenant);

        // Self-reference for RLS: tenantId == id
        saved.setTenantId(saved.getId());
        saved = tenantRepository.save(saved);

        seedDefaultConfigs(saved.getId());

        log.info("Created tenant: tenantId={}, slug={}", saved.getId(), saved.getSlug());

        TenantOnboardedEvent event = new TenantOnboardedEvent(
                saved.getId(),
                saved.getSlug(),
                saved.getName(),
                saved.getPlanType(),
                Instant.now()
        );
        tenantEventPublisher.publishTenantOnboarded(event);

        return toResponse(saved);
    }

    /**
     * Retrieves a tenant by its URL slug.
     *
     * @param slug the unique slug to look up
     * @return the matching tenant response
     * @throws TenantNotFoundException if no tenant exists with the given slug
     */
    @Transactional(readOnly = true)
    public TenantResponse getBySlug(String slug) {
        Tenant tenant = tenantRepository.findBySlug(slug)
                .orElseThrow(() -> new TenantNotFoundException(slug));
        return toResponse(tenant);
    }

    /**
     * Retrieves a tenant by its UUID.
     *
     * @param id the tenant UUID
     * @return the matching tenant response
     * @throws TenantNotFoundException if no tenant exists with the given ID
     */
    @Transactional(readOnly = true)
    public TenantResponse getById(UUID id) {
        Tenant tenant = tenantRepository.findById(id)
                .orElseThrow(() -> new TenantNotFoundException(id.toString()));
        return toResponse(tenant);
    }

    /**
     * Upserts configuration entries for the given tenant.
     *
     * <p>For each key in {@code req.configs()}: if a config entry with that key already exists
     * for the tenant, its value is updated; otherwise a new entry is inserted.
     *
     * @param tenantId the UUID of the tenant whose config to update
     * @param req      the request containing key-value pairs to upsert
     * @return the full configuration response after the upsert
     * @throws TenantNotFoundException if no tenant exists with the given ID
     */
    public TenantConfigResponse updateConfig(UUID tenantId, UpdateTenantConfigRequest req) {
        // Verify tenant exists
        tenantRepository.findById(tenantId)
                .orElseThrow(() -> new TenantNotFoundException(tenantId.toString()));

        if (req.configs() != null) {
            req.configs().forEach((key, value) -> {
                TenantConfig config = tenantConfigRepository
                        .findByTenantIdAndConfigKey(tenantId, key)
                        .orElseGet(() -> TenantConfig.builder()
                                .tenantId(tenantId)
                                .configKey(key)
                                .build());
                config.setConfigValue(value);
                tenantConfigRepository.save(config);
            });
        }

        log.info("Updated config for tenantId={}, keys={}", tenantId,
                req.configs() != null ? req.configs().keySet() : "[]");

        return buildConfigResponse(tenantId);
    }

    /**
     * Returns all configuration entries for the given tenant as a map.
     *
     * @param tenantId the UUID of the tenant
     * @return the full configuration response
     * @throws TenantNotFoundException if no tenant exists with the given ID
     */
    @Transactional(readOnly = true)
    public TenantConfigResponse getConfig(UUID tenantId) {
        tenantRepository.findById(tenantId)
                .orElseThrow(() -> new TenantNotFoundException(tenantId.toString()));

        return buildConfigResponse(tenantId);
    }

    /**
     * Updates the lifecycle status of the given tenant.
     *
     * @param tenantId the UUID of the tenant
     * @param req      the request containing the new status
     * @return the updated tenant response
     * @throws TenantNotFoundException if no tenant exists with the given ID
     */
    public TenantResponse updateStatus(UUID tenantId, UpdateTenantStatusRequest req) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new TenantNotFoundException(tenantId.toString()));

        tenant.setStatus(req.status());
        Tenant saved = tenantRepository.save(tenant);

        log.info("Updated status for tenantId={}, newStatus={}", tenantId, req.status());

        return toResponse(saved);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private void seedDefaultConfigs(UUID tenantId) {
        List<TenantConfig> defaults = List.of(
                TenantConfig.builder()
                        .tenantId(tenantId)
                        .configKey(DEFAULT_SLA_RESPONSE_HOURS_KEY)
                        .configValue("4")
                        .build(),
                TenantConfig.builder()
                        .tenantId(tenantId)
                        .configKey(DEFAULT_SLA_RESOLUTION_HOURS_KEY)
                        .configValue("24")
                        .build(),
                TenantConfig.builder()
                        .tenantId(tenantId)
                        .configKey(DEFAULT_BRANDING_PRIMARY_COLOR_KEY)
                        .configValue("#007AFF")
                        .build()
        );
        tenantConfigRepository.saveAll(defaults);
        log.info("Seeded default configs for tenantId={}", tenantId);
    }

    private TenantConfigResponse buildConfigResponse(UUID tenantId) {
        List<TenantConfig> configs = tenantConfigRepository.findAllByTenantId(tenantId);
        Map<String, String> configMap = configs.stream()
                .collect(Collectors.toMap(TenantConfig::getConfigKey, TenantConfig::getConfigValue));
        return new TenantConfigResponse(tenantId, configMap);
    }

    private TenantResponse toResponse(Tenant tenant) {
        return new TenantResponse(
                tenant.getId(),
                tenant.getSlug(),
                tenant.getName(),
                tenant.getPlanType(),
                tenant.getStatus(),
                tenant.getTimezone(),
                tenant.getLocale(),
                tenant.getCreatedAt()
        );
    }
}
