package in.supporthub.ticket.repository;

import in.supporthub.ticket.domain.Ticket;
import in.supporthub.ticket.domain.TicketStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Data access layer for {@link Ticket} entities.
 *
 * <p>All queries include {@code tenantId} to enforce tenant isolation.
 * PostgreSQL RLS provides an additional database-level enforcement layer.
 */
public interface TicketRepository extends JpaRepository<Ticket, UUID>, JpaSpecificationExecutor<Ticket> {

    /**
     * Finds a ticket by its tenant and human-readable ticket number.
     *
     * @param tenantId     the tenant UUID
     * @param ticketNumber the formatted ticket number (e.g., "FC-2024-001234")
     * @return an Optional containing the ticket if found
     */
    Optional<Ticket> findByTenantIdAndTicketNumber(UUID tenantId, String ticketNumber);

    /**
     * Returns a page of tickets belonging to a specific customer within a tenant.
     *
     * @param tenantId   the tenant UUID
     * @param customerId the customer UUID
     * @param pageable   pagination parameters
     * @return a page of tickets
     */
    Page<Ticket> findByTenantIdAndCustomerId(UUID tenantId, UUID customerId, Pageable pageable);

    /**
     * Returns a page of tickets with a specific status within a tenant.
     *
     * @param tenantId the tenant UUID
     * @param status   the ticket status to filter by
     * @param pageable pagination parameters
     * @return a page of tickets
     */
    Page<Ticket> findByTenantIdAndStatus(UUID tenantId, TicketStatus status, Pageable pageable);

    /**
     * Finds tickets whose SLA resolution deadline has passed and that are not in a terminal status.
     *
     * <p>Used by the {@link in.supporthub.ticket.service.SlaEngine} in the scheduled breach detection job.
     *
     * @param now              the current timestamp
     * @param excludedStatuses statuses to exclude (typically RESOLVED, CLOSED)
     * @return list of tickets that are breaching their SLA
     */
    @Query("SELECT t FROM Ticket t WHERE t.slaResolutionDueAt < :now " +
            "AND t.status NOT IN :excludedStatuses " +
            "AND t.slaResolutionBreached = false")
    List<Ticket> findSlaBreaching(
            @Param("now") Instant now,
            @Param("excludedStatuses") List<TicketStatus> excludedStatuses);

    /**
     * Finds tickets whose first-response SLA deadline has passed and that have not yet been responded to.
     *
     * @param now              the current timestamp
     * @param excludedStatuses statuses to exclude (typically RESOLVED, CLOSED)
     * @return list of tickets that are breaching their first-response SLA
     */
    @Query("SELECT t FROM Ticket t WHERE t.slaFirstResponseDueAt < :now " +
            "AND t.firstRespondedAt IS NULL " +
            "AND t.status NOT IN :excludedStatuses " +
            "AND t.slaFirstResponseBreached = false")
    List<Ticket> findFirstResponseSlaBreaching(
            @Param("now") Instant now,
            @Param("excludedStatuses") List<TicketStatus> excludedStatuses);

    /**
     * Counts tickets with a specific status for a tenant.
     *
     * @param tenantId the tenant UUID
     * @param status   the ticket status
     * @return count of matching tickets
     */
    long countByTenantIdAndStatus(UUID tenantId, TicketStatus status);
}
