package in.supporthub.ticket.repository;

import in.supporthub.ticket.domain.Priority;
import in.supporthub.ticket.domain.SlaPolicy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Data access layer for {@link SlaPolicy} entities.
 *
 * <p>Matching precedence (most specific wins):
 * <ol>
 *   <li>category + priority</li>
 *   <li>category only</li>
 *   <li>priority only</li>
 *   <li>default (both null)</li>
 * </ol>
 */
public interface SlaPolicyRepository extends JpaRepository<SlaPolicy, UUID> {

    /**
     * Returns all active SLA policies for a given tenant, category, and priority combination.
     *
     * <p>The caller is responsible for selecting the most-specific policy from the returned list.
     *
     * @param tenantId   the tenant UUID
     * @param categoryId the category UUID
     * @param active     pass {@code true} to get only active policies
     * @return list of matching SLA policies
     */
    List<SlaPolicy> findByTenantIdAndCategoryIdAndActive(UUID tenantId, UUID categoryId, boolean active);

    /**
     * Returns all active SLA policies matching a tenant and priority (category is irrelevant).
     *
     * @param tenantId the tenant UUID
     * @param priority the ticket priority
     * @param active   pass {@code true} to get only active policies
     * @return list of matching SLA policies
     */
    List<SlaPolicy> findByTenantIdAndPriorityAndActive(UUID tenantId, Priority priority, boolean active);

    /**
     * Returns the default SLA policies for a tenant (no category, no priority constraint).
     *
     * @param tenantId the tenant UUID
     * @param active   pass {@code true} to get only active policies
     * @return list of default SLA policies
     */
    List<SlaPolicy> findByTenantIdAndCategoryIdIsNullAndPriorityIsNullAndActive(
            UUID tenantId, boolean active);
}
