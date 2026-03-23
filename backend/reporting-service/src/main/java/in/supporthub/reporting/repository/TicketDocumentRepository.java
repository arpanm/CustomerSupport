package in.supporthub.reporting.repository;

import in.supporthub.reporting.domain.TicketDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Elasticsearch repository for {@link TicketDocument} read models.
 *
 * <p>All queries are automatically tenant-scoped by passing tenantId as a filter parameter.
 * Never query without a tenantId to ensure multi-tenant isolation.
 */
@Repository
public interface TicketDocumentRepository extends ElasticsearchRepository<TicketDocument, String> {

    /**
     * Finds all ticket documents for a given tenant and status.
     *
     * @param tenantId the tenant identifier
     * @param status   the ticket status (e.g., "OPEN", "RESOLVED")
     * @return list of matching ticket documents
     */
    List<TicketDocument> findByTenantIdAndStatus(String tenantId, String status);

    /**
     * Counts tickets for a given tenant and status.
     *
     * @param tenantId the tenant identifier
     * @param status   the ticket status
     * @return count of matching tickets
     */
    long countByTenantIdAndStatus(String tenantId, String status);

    /**
     * Counts tickets for a given tenant where the SLA has been breached.
     *
     * @param tenantId the tenant identifier
     * @return count of SLA-breached tickets
     */
    long countByTenantIdAndSlaBreachedTrue(String tenantId);

    /**
     * Counts all tickets for a given tenant.
     *
     * @param tenantId the tenant identifier
     * @return total ticket count for the tenant
     */
    long countByTenantId(String tenantId);

    /**
     * Finds all ticket documents for a given tenant.
     *
     * @param tenantId the tenant identifier
     * @return list of all ticket documents for the tenant
     */
    List<TicketDocument> findByTenantId(String tenantId);
}
