package in.supporthub.tenant.repository;

import in.supporthub.tenant.domain.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * JPA repository for {@link Tenant} entities.
 *
 * <p>All queries are automatically scoped to the current tenant via PostgreSQL RLS.
 * The application-level filter sets the {@code app.tenant_id} session variable before
 * any query executes.
 */
@Repository
public interface TenantRepository extends JpaRepository<Tenant, UUID> {

    /**
     * Finds a tenant by its URL slug.
     *
     * @param slug the unique URL-friendly tenant identifier
     * @return the matching tenant, or empty if not found
     */
    Optional<Tenant> findBySlug(String slug);

    /**
     * Finds a tenant by its tenantId column (self-reference for RLS compatibility).
     *
     * @param tenantId the tenant UUID
     * @return the matching tenant, or empty if not found
     */
    Optional<Tenant> findByTenantId(UUID tenantId);

    /**
     * Checks whether a tenant with the given slug already exists.
     *
     * @param slug the slug to check
     * @return {@code true} if a tenant with this slug exists
     */
    boolean existsBySlug(String slug);
}
