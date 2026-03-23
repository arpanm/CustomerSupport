package in.supporthub.ordersync.repository;

import in.supporthub.ordersync.domain.OmsConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * JPA repository for {@link OmsConfig} entities.
 *
 * <p>Provides tenant-scoped lookup of OMS configuration.
 * Each tenant has at most one config record, enforced by the UNIQUE constraint
 * on the {@code tenant_id} column.
 */
@Repository
public interface OmsConfigRepository extends JpaRepository<OmsConfig, UUID> {

    /**
     * Finds the OMS configuration for the given tenant.
     *
     * @param tenantId the tenant UUID
     * @return an {@link Optional} containing the config if present
     */
    Optional<OmsConfig> findByTenantId(UUID tenantId);

    /**
     * Checks whether an active OMS configuration exists for the given tenant.
     *
     * @param tenantId the tenant UUID
     * @param isActive whether to filter by active state
     * @return true if such a config exists
     */
    boolean existsByTenantIdAndIsActive(UUID tenantId, boolean isActive);
}
