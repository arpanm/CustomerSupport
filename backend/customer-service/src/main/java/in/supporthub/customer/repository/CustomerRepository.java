package in.supporthub.customer.repository;

import in.supporthub.customer.domain.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link Customer} entities.
 *
 * <p>All queries include {@code tenantId} to enforce tenant isolation at the query level,
 * in addition to the PostgreSQL Row-Level Security policy on the underlying table.
 */
@Repository
public interface CustomerRepository extends JpaRepository<Customer, UUID> {

    /**
     * Finds a customer by tenant and primary key.
     *
     * @param tenantId the owning tenant UUID
     * @param id       the customer UUID
     * @return the customer if found and belonging to the specified tenant
     */
    Optional<Customer> findByTenantIdAndId(UUID tenantId, UUID id);

    /**
     * Finds a customer by tenant and the SHA-256 hash of their normalised phone number.
     *
     * <p>Used by the auth-service login flow and for deduplication checks.
     *
     * @param tenantId  the owning tenant UUID
     * @param phoneHash SHA-256 hash of the normalised phone number (never raw phone)
     * @return the customer if found
     */
    Optional<Customer> findByTenantIdAndPhoneHash(UUID tenantId, String phoneHash);
}
