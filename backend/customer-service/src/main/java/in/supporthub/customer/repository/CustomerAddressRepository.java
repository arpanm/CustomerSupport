package in.supporthub.customer.repository;

import in.supporthub.customer.domain.CustomerAddress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link CustomerAddress} entities.
 *
 * <p>All queries include {@code tenantId} to enforce tenant isolation at the query level,
 * in addition to the PostgreSQL Row-Level Security policy on the underlying table.
 */
@Repository
public interface CustomerAddressRepository extends JpaRepository<CustomerAddress, UUID> {

    /**
     * Returns all saved addresses for a specific customer within a tenant.
     *
     * @param tenantId   the owning tenant UUID
     * @param customerId the customer UUID
     * @return list of addresses (may be empty)
     */
    List<CustomerAddress> findAllByTenantIdAndCustomerId(UUID tenantId, UUID customerId);

    /**
     * Finds a specific address by tenant, address ID, and owning customer.
     *
     * <p>The triple-key lookup ensures customers cannot access addresses belonging to other
     * customers within the same tenant.
     *
     * @param tenantId   the owning tenant UUID
     * @param id         the address UUID
     * @param customerId the customer UUID — prevents cross-customer address access
     * @return the address if found and owned by the specified customer
     */
    Optional<CustomerAddress> findByTenantIdAndIdAndCustomerId(UUID tenantId, UUID id, UUID customerId);
}
