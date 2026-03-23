package in.supporthub.ticket.service;

import in.supporthub.ticket.domain.Ticket;
import in.supporthub.ticket.domain.TicketStatus;
import in.supporthub.ticket.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * SLA computation and breach detection engine.
 *
 * <p>Computes SLA deadlines at ticket creation time and runs a scheduled job
 * every 5 minutes to detect tickets that have breached their SLA thresholds.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SlaEngine {

    private static final List<TicketStatus> TERMINAL_STATUSES =
            List.of(TicketStatus.RESOLVED, TicketStatus.CLOSED);

    private final TicketRepository ticketRepository;
    private final TicketEventPublisher ticketEventPublisher;

    /**
     * Computes SLA deadlines based on ticket creation time and policy hours.
     *
     * @param ticket               the ticket for which to compute deadlines
     * @param firstResponseHours   hours allowed for first agent response
     * @param resolutionHours      hours allowed for full resolution
     * @return computed SLA deadlines
     */
    public SlaDeadlines compute(Ticket ticket, int firstResponseHours, int resolutionHours) {
        Instant base = ticket.getCreatedAt() != null ? ticket.getCreatedAt() : Instant.now();
        return new SlaDeadlines(
                base.plus(firstResponseHours, ChronoUnit.HOURS),
                base.plus(resolutionHours, ChronoUnit.HOURS)
        );
    }

    /**
     * Scheduled SLA breach detection job — runs every 5 minutes.
     *
     * <p>Finds all non-terminal tickets whose resolution SLA deadline has passed
     * and marks them as breached. Also checks first-response SLA for tickets that
     * have not yet received an agent response.
     */
    @Scheduled(fixedDelay = 300_000)
    @Transactional
    public void detectBreaches() {
        log.info("SLA breach detection job started");
        Instant now = Instant.now();

        int resolutionBreachCount = detectResolutionBreaches(now);
        int firstResponseBreachCount = detectFirstResponseBreaches(now);

        log.info("SLA breach detection completed: resolutionBreaches={}, firstResponseBreaches={}",
                resolutionBreachCount, firstResponseBreachCount);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private int detectResolutionBreaches(Instant now) {
        List<Ticket> breachingTickets = ticketRepository.findSlaBreaching(now, TERMINAL_STATUSES);

        for (Ticket ticket : breachingTickets) {
            ticket.setSlaResolutionBreached(true);
            ticketRepository.save(ticket);
            log.warn("SLA resolution breached: ticketId={}, tenantId={}, ticketNumber={}, dueAt={}",
                    ticket.getId(), ticket.getTenantId(), ticket.getTicketNumber(),
                    ticket.getSlaResolutionDueAt());
        }

        return breachingTickets.size();
    }

    private int detectFirstResponseBreaches(Instant now) {
        List<Ticket> breachingTickets = ticketRepository.findFirstResponseSlaBreaching(now, TERMINAL_STATUSES);

        for (Ticket ticket : breachingTickets) {
            ticket.setSlaFirstResponseBreached(true);
            ticketRepository.save(ticket);
            log.warn("SLA first-response breached: ticketId={}, tenantId={}, ticketNumber={}, dueAt={}",
                    ticket.getId(), ticket.getTenantId(), ticket.getTicketNumber(),
                    ticket.getSlaFirstResponseDueAt());
        }

        return breachingTickets.size();
    }
}
