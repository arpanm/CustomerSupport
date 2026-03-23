package in.supporthub.auth.repository;

import in.supporthub.auth.domain.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link Customer} entities.
 *
 * <p>All queries are scoped to a {@code tenantId} to enforce tenant isolation.
 * Never call find methods without passing a tenantId.
 */
@Repository
public interface CustomerRepository extends JpaRepository<Customer, UUID> {

    /**
     * Look up a customer by tenant and hashed phone number.
     *
     * @param tenantId  tenant UUID
     * @param phoneHash HMAC-SHA256 digest of the E.164 phone number
     * @return matching customer, or empty if not found
     */
    Optional<Customer> findByTenantIdAndPhoneHash(UUID tenantId, String phoneHash);

    /**
     * Check whether a customer with the given phone hash already exists for a tenant.
     *
     * @param tenantId  tenant UUID
     * @param phoneHash HMAC-SHA256 digest of the E.164 phone number
     * @return {@code true} if at least one matching record exists
     */
    boolean existsByTenantIdAndPhoneHash(UUID tenantId, String phoneHash);
}
