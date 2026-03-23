package in.supporthub.ticket.repository;

import in.supporthub.ticket.domain.TicketActivity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Data access layer for {@link TicketActivity} entities.
 *
 * <p>Activities are append-only; this repository provides no update or delete operations.
 */
public interface TicketActivityRepository extends JpaRepository<TicketActivity, UUID> {

    /**
     * Returns all activities for a ticket in chronological order (oldest first).
     *
     * @param ticketId the parent ticket UUID
     * @return ordered list of activities
     */
    List<TicketActivity> findByTicketIdOrderByCreatedAtAsc(UUID ticketId);

    /**
     * Returns all activities for a ticket that are visible to the customer (non-internal).
     *
     * @param ticketId   the parent ticket UUID
     * @param internal   pass {@code false} to get customer-visible activities
     * @return ordered list of activities
     */
    List<TicketActivity> findByTicketIdAndInternalOrderByCreatedAtAsc(UUID ticketId, boolean internal);
}
