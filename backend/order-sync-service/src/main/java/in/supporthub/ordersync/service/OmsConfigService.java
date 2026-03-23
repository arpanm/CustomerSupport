package in.supporthub.ordersync.service;

import in.supporthub.ordersync.domain.OmsConfig;
import in.supporthub.ordersync.dto.OmsConfigRequest;
import in.supporthub.ordersync.exception.OmsConfigNotFoundException;
import in.supporthub.ordersync.repository.OmsConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Manages OMS configuration lifecycle for tenants.
 *
 * <p>API keys are encrypted before persistence using {@link PiiEncryptionService}.
 * The plaintext API key is NEVER stored and NEVER logged.
 *
 * <p>All mutation methods are annotated with {@link Transactional} at the class level.
 */
@Service
@Transactional
@Slf4j
@RequiredArgsConstructor
public class OmsConfigService {

    private final OmsConfigRepository omsConfigRepository;
    private final PiiEncryptionService piiEncryptionService;

    /**
     * Creates or replaces the OMS configuration for a tenant.
     *
     * <p>If a config already exists for the tenant it is updated in place.
     * The API key is encrypted before saving; the plaintext value is not retained.
     *
     * @param tenantId the tenant UUID (from JWT context — never from request body)
     * @param req      configuration details, including plaintext API key
     */
    public void saveConfig(UUID tenantId, OmsConfigRequest req) {
        OmsConfig config = omsConfigRepository.findByTenantId(tenantId)
                .orElseGet(OmsConfig::new);

        config.setTenantId(tenantId);
        config.setOmsBaseUrl(req.omsBaseUrl());
        config.setApiKeyEncrypted(piiEncryptionService.encrypt(req.apiKey()));
        config.setAuthType(req.authType() != null ? req.authType() : "BEARER");
        config.setHeaderName(req.headerName() != null ? req.headerName() : "Authorization");
        config.setActive(true);

        omsConfigRepository.save(config);
        log.info("OMS config saved: tenantId={}", tenantId);
    }

    /**
     * Retrieves the OMS configuration for a tenant.
     *
     * @param tenantId the tenant UUID
     * @return the {@link OmsConfig} entity
     * @throws OmsConfigNotFoundException if no config exists for the tenant
     */
    @Transactional(readOnly = true)
    public OmsConfig getConfig(UUID tenantId) {
        return omsConfigRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new OmsConfigNotFoundException(tenantId));
    }

    /**
     * Deletes the OMS configuration for a tenant.
     *
     * <p>If no config exists, this method returns silently.
     *
     * @param tenantId the tenant UUID
     */
    public void deleteConfig(UUID tenantId) {
        omsConfigRepository.findByTenantId(tenantId).ifPresent(config -> {
            omsConfigRepository.delete(config);
            log.info("OMS config deleted: tenantId={}", tenantId);
        });
    }
}
