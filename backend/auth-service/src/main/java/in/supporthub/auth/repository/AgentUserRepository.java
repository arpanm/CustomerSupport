package in.supporthub.auth.repository;

import in.supporthub.auth.domain.AgentUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link AgentUser} entities.
 *
 * <p>All queries are scoped to a {@code tenantId} to enforce tenant isolation.
 * Never call find methods without passing a tenantId.
 */
@Repository
public interface AgentUserRepository extends JpaRepository<AgentUser, UUID> {

    /**
     * Look up an agent by tenant and email address.
     *
     * @param tenantId tenant UUID
     * @param email    agent login email (case-sensitive)
     * @return matching agent, or empty if not found
     */
    Optional<AgentUser> findByTenantIdAndEmail(UUID tenantId, String email);

    /**
     * Look up an active agent by tenant and email address.
     *
     * @param tenantId tenant UUID
     * @param email    agent login email (case-sensitive)
     * @return active agent, or empty if not found or deactivated
     */
    Optional<AgentUser> findByTenantIdAndEmailAndIsActiveTrue(UUID tenantId, String email);
}
