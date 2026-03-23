package in.supporthub.ticket.repository;

import in.supporthub.ticket.domain.TicketCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Data access layer for {@link TicketCategory} entities.
 */
public interface TicketCategoryRepository extends JpaRepository<TicketCategory, UUID> {

    /**
     * Returns all active (or all) categories for a tenant, ordered by sort position.
     *
     * @param tenantId the tenant UUID
     * @param active   {@code true} to get only active categories
     * @return list of categories ordered by sortOrder ascending
     */
    List<TicketCategory> findByTenantIdAndActiveOrderBySortOrderAsc(UUID tenantId, boolean active);

    /**
     * Finds a category by its URL-safe slug within a tenant.
     *
     * @param tenantId the tenant UUID
     * @param slug     the category slug
     * @return an Optional containing the category if found
     */
    Optional<TicketCategory> findByTenantIdAndSlug(UUID tenantId, String slug);
}
