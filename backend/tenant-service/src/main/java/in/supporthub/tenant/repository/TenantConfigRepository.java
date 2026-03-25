package in.supporthub.tenant.repository;

import in.supporthub.tenant.domain.TenantConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * JPA repository for {@link TenantConfig} key-value entries.
 *
 * <p>Config entries are scoped to a specific tenant. All mutation operations should be
 * performed within a transaction to ensure atomicity when upserting multiple keys.
 */
@Repository
public interface TenantConfigRepository extends JpaRepository<TenantConfig, UUID> {

    /**
     * Returns all configuration entries for the given tenant.
     *
     * @param tenantId the tenant UUID
     * @return list of config entries, possibly empty
     */
    List<TenantConfig> findAllByTenantId(UUID tenantId);

    /**
     * Finds a specific configuration entry by tenant and key.
     *
     * @param tenantId  the tenant UUID
     * @param configKey the configuration key
     * @return the matching config entry, or empty if not found
     */
    Optional<TenantConfig> findByTenantIdAndConfigKey(UUID tenantId, String configKey);

    /**
     * Deletes all configuration entries for the given tenant.
     * Should only be called when a tenant is being fully deprovisioned.
     *
     * @param tenantId the tenant UUID whose configs to delete
     */
    void deleteAllByTenantId(UUID tenantId);
}
