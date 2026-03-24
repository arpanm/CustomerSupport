package in.supporthub.ticket.repository;

import in.supporthub.ticket.domain.Attachment;
import in.supporthub.ticket.domain.AttachmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link Attachment} entities.
 *
 * <p>All queries are scoped to a single tenant via the {@code tenantId} parameter.
 * Row-Level Security at the database layer provides an additional enforcement boundary.
 */
@Repository
public interface AttachmentRepository extends JpaRepository<Attachment, UUID> {

    /**
     * Finds all attachments belonging to a specific ticket within a tenant.
     *
     * @param ticketId the ticket UUID
     * @param tenantId the tenant UUID
     * @return list of matching attachments
     */
    List<Attachment> findByTicketIdAndTenantId(UUID ticketId, UUID tenantId);

    /**
     * Finds all attachments by their IDs within a tenant.
     * Used during ticket creation to validate the supplied attachment IDs.
     *
     * @param ids      the list of attachment UUIDs from the CreateTicketRequest
     * @param tenantId the tenant UUID
     * @return list of matching attachments
     */
    List<Attachment> findByIdInAndTenantId(List<UUID> ids, UUID tenantId);

    /**
     * Links a batch of PENDING attachments to a specific ticket and marks them as LINKED.
     *
     * @param ids      the attachment UUIDs to update
     * @param ticketId the target ticket UUID
     * @param tenantId the tenant UUID (safety guard)
     */
    @Modifying
    @Query("UPDATE Attachment a SET a.ticketId = :ticketId, a.status = :status WHERE a.id IN :ids AND a.tenantId = :tenantId")
    void linkToTicket(
            @Param("ids") List<UUID> ids,
            @Param("ticketId") UUID ticketId,
            @Param("status") AttachmentStatus status,
            @Param("tenantId") UUID tenantId
    );
}
