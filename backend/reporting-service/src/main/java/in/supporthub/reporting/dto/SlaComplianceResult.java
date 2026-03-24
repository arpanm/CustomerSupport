package in.supporthub.reporting.dto;

/**
 * SLA compliance metrics for a single ticket category.
 *
 * @param categoryName      Human-readable category name (or ID if name unavailable).
 * @param totalTickets      Total tickets in the category for the reporting period.
 * @param onTimeTickets     Tickets resolved within SLA (not breached).
 * @param compliancePercent Percentage of tickets resolved on time (0.0–100.0).
 */
public record SlaComplianceResult(
        String categoryName,
        long totalTickets,
        long onTimeTickets,
        double compliancePercent) {
}
