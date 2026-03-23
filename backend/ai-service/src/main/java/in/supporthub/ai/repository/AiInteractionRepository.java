package in.supporthub.ai.repository;

import in.supporthub.ai.domain.AiInteraction;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * MongoDB repository for {@link AiInteraction} documents.
 *
 * <p>Used to persist audit records of all AI model calls (sentiment + resolution suggestions).
 * Queries are tenant-scoped; always pass both {@code tenantId} and {@code ticketId} when
 * retrieving interactions.
 */
@Repository
public interface AiInteractionRepository extends MongoRepository<AiInteraction, String> {

    /**
     * Returns all AI interactions for a specific ticket within a tenant.
     *
     * @param tenantId the tenant UUID
     * @param ticketId the ticket UUID
     * @return ordered list of interactions (oldest first per MongoDB default)
     */
    List<AiInteraction> findByTenantIdAndTicketId(String tenantId, String ticketId);
}
