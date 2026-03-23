package in.supporthub.reporting.dto;

import java.time.Instant;

/**
 * Pre-computed summary metrics for the reporting dashboard.
 *
 * @param totalTickets             Total number of tickets in the period.
 * @param openTickets              Tickets with status OPEN at period end.
 * @param resolvedTickets          Tickets resolved within the period.
 * @param avgResolutionTimeMinutes Average resolution time in minutes for resolved tickets.
 * @param slaBreachCount           Number of tickets where SLA was breached.
 * @param slaBreachRate            Fraction of tickets (0.0–1.0) where SLA was breached.
 * @param periodFrom               Inclusive start of the reporting period.
 * @param periodTo                 Exclusive end of the reporting period.
 */
public record DashboardSummary(
        long totalTickets,
        long openTickets,
        long resolvedTickets,
        double avgResolutionTimeMinutes,
        long slaBreachCount,
        double slaBreachRate,
        Instant periodFrom,
        Instant periodTo) {
}
