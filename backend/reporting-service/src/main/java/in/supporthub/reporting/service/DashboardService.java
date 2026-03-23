package in.supporthub.reporting.service;

import in.supporthub.reporting.domain.TicketDocument;
import in.supporthub.reporting.dto.AgentMetrics;
import in.supporthub.reporting.dto.CategoryCount;
import in.supporthub.reporting.dto.DashboardSummary;
import in.supporthub.reporting.dto.TrendPoint;
import in.supporthub.reporting.repository.TicketDocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.stream.Collectors;

/**
 * Service for computing dashboard metrics and analytical reports from the Elasticsearch read model.
 *
 * <p>All queries are tenant-scoped. Period filtering is performed in-memory after fetching
 * tenant documents; for large datasets a native Elasticsearch query approach is preferred
 * but this implementation provides a functional baseline.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DashboardService {

    private static final String GRANULARITY_WEEKLY = "weekly";
    private static final String RESOLVED_STATUS = "RESOLVED";
    private static final String OPEN_STATUS = "OPEN";

    private static final DateTimeFormatter DAILY_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter WEEKLY_FMT =
            DateTimeFormatter.ofPattern("YYYY-'W'ww").withZone(ZoneOffset.UTC);

    private final TicketDocumentRepository ticketDocumentRepository;

    /**
     * Returns a pre-computed summary of ticket metrics for the given tenant and time period.
     *
     * @param tenantId tenant identifier
     * @param from     inclusive start of the reporting period
     * @param to       exclusive end of the reporting period
     * @return {@link DashboardSummary} with aggregated counts and rates
     */
    public DashboardSummary getDashboardSummary(String tenantId, Instant from, Instant to) {
        log.debug("Computing dashboard summary: tenantId={}, from={}, to={}", tenantId, from, to);

        List<TicketDocument> docs = ticketDocumentRepository.findByTenantId(tenantId)
                .stream()
                .filter(d -> isInPeriod(d.getCreatedAt(), from, to))
                .toList();

        long totalTickets = docs.size();
        long openTickets = docs.stream()
                .filter(d -> OPEN_STATUS.equalsIgnoreCase(d.getStatus()))
                .count();
        long resolvedTickets = docs.stream()
                .filter(d -> RESOLVED_STATUS.equalsIgnoreCase(d.getStatus()))
                .count();
        long slaBreachCount = docs.stream()
                .filter(d -> Boolean.TRUE.equals(d.getSlaBreached()))
                .count();

        OptionalDouble avgOpt = docs.stream()
                .filter(d -> d.getResolutionTimeMinutes() != null)
                .mapToLong(TicketDocument::getResolutionTimeMinutes)
                .average();
        double avgResolutionTimeMinutes = avgOpt.orElse(0.0);

        double slaBreachRate = (totalTickets > 0)
                ? (double) slaBreachCount / totalTickets
                : 0.0;

        return new DashboardSummary(
                totalTickets,
                openTickets,
                resolvedTickets,
                avgResolutionTimeMinutes,
                slaBreachCount,
                slaBreachRate,
                from,
                to
        );
    }

    /**
     * Returns a map of status → count for all tickets of the given tenant.
     *
     * <p>Suitable for pie/donut charts on the dashboard.
     *
     * @param tenantId tenant identifier
     * @return map where keys are status strings and values are ticket counts
     */
    public Map<String, Long> getTicketsByStatus(String tenantId) {
        log.debug("Computing tickets by status: tenantId={}", tenantId);

        List<TicketDocument> docs = ticketDocumentRepository.findByTenantId(tenantId);

        return docs.stream()
                .collect(Collectors.groupingBy(
                        d -> d.getStatus() != null ? d.getStatus() : "UNKNOWN",
                        Collectors.counting()
                ));
    }

    /**
     * Returns ticket counts grouped by category for the given tenant and period.
     *
     * <p>Category names are not enriched here (no cross-service call per architecture rules).
     * The caller or API layer may enrich category names if needed.
     *
     * @param tenantId tenant identifier
     * @param from     inclusive start of the reporting period
     * @param to       exclusive end of the reporting period
     * @return list of {@link CategoryCount}, sorted by count descending
     */
    public List<CategoryCount> getTicketsByCategory(String tenantId, Instant from, Instant to) {
        log.debug("Computing tickets by category: tenantId={}, from={}, to={}", tenantId, from, to);

        List<TicketDocument> docs = ticketDocumentRepository.findByTenantId(tenantId)
                .stream()
                .filter(d -> isInPeriod(d.getCreatedAt(), from, to))
                .toList();

        Map<String, Long> counts = docs.stream()
                .filter(d -> d.getCategoryId() != null)
                .collect(Collectors.groupingBy(TicketDocument::getCategoryId, Collectors.counting()));

        return counts.entrySet().stream()
                .map(e -> new CategoryCount(e.getKey(), "", e.getValue()))
                .sorted((a, b) -> Long.compare(b.count(), a.count()))
                .collect(Collectors.toList());
    }

    /**
     * Returns time-series ticket volume data for the given tenant and period.
     *
     * @param tenantId    tenant identifier
     * @param from        inclusive start of the period
     * @param to          exclusive end of the period
     * @param granularity {@code "daily"} or {@code "weekly"}
     * @return ordered list of {@link TrendPoint} objects
     */
    public List<TrendPoint> getTicketTrend(
            String tenantId, Instant from, Instant to, String granularity) {

        log.debug("Computing ticket trend: tenantId={}, from={}, to={}, granularity={}",
                tenantId, from, to, granularity);

        List<TicketDocument> docs = ticketDocumentRepository.findByTenantId(tenantId)
                .stream()
                .filter(d -> isInPeriod(d.getCreatedAt(), from, to))
                .toList();

        boolean isWeekly = GRANULARITY_WEEKLY.equalsIgnoreCase(granularity);
        DateTimeFormatter fmt = isWeekly ? WEEKLY_FMT : DAILY_FMT;

        Map<String, Long> buckets = new HashMap<>();
        for (TicketDocument doc : docs) {
            if (doc.getCreatedAt() == null) {
                continue;
            }
            String bucket = fmt.format(doc.getCreatedAt());
            buckets.merge(bucket, 1L, Long::sum);
        }

        return buckets.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> new TrendPoint(e.getKey(), e.getValue()))
                .collect(Collectors.toList());
    }

    /**
     * Returns per-agent performance metrics for the given tenant and period.
     *
     * <p>Only tickets with an assigned agent are included.
     *
     * @param tenantId tenant identifier
     * @param from     inclusive start of the period
     * @param to       exclusive end of the period
     * @return list of {@link AgentMetrics}, sorted by resolved count descending
     */
    public List<AgentMetrics> getAgentPerformance(String tenantId, Instant from, Instant to) {
        log.debug("Computing agent performance: tenantId={}, from={}, to={}", tenantId, from, to);

        List<TicketDocument> docs = ticketDocumentRepository.findByTenantId(tenantId)
                .stream()
                .filter(d -> isInPeriod(d.getCreatedAt(), from, to))
                .filter(d -> d.getAssignedAgentId() != null && !d.getAssignedAgentId().isBlank())
                .toList();

        Map<String, List<TicketDocument>> byAgent = docs.stream()
                .collect(Collectors.groupingBy(TicketDocument::getAssignedAgentId));

        List<AgentMetrics> result = new ArrayList<>();
        for (Map.Entry<String, List<TicketDocument>> entry : byAgent.entrySet()) {
            String agentId = entry.getKey();
            List<TicketDocument> agentDocs = entry.getValue();

            long assignedCount = agentDocs.size();
            long resolvedCount = agentDocs.stream()
                    .filter(d -> RESOLVED_STATUS.equalsIgnoreCase(d.getStatus()))
                    .count();

            OptionalDouble avgOpt = agentDocs.stream()
                    .filter(d -> d.getResolutionTimeMinutes() != null)
                    .mapToLong(TicketDocument::getResolutionTimeMinutes)
                    .average();
            double avgResolutionMinutes = avgOpt.orElse(0.0);

            result.add(new AgentMetrics(agentId, assignedCount, resolvedCount, avgResolutionMinutes));
        }

        result.sort((a, b) -> Long.compare(b.resolvedCount(), a.resolvedCount()));
        return result;
    }

    /**
     * Returns the distribution of sentiment labels for the given tenant and period.
     *
     * @param tenantId tenant identifier
     * @param from     inclusive start of the period
     * @param to       exclusive end of the period
     * @return map of sentimentLabel → count
     */
    public Map<String, Long> getSentimentDistribution(String tenantId, Instant from, Instant to) {
        log.debug("Computing sentiment distribution: tenantId={}, from={}, to={}", tenantId, from, to);

        List<TicketDocument> docs = ticketDocumentRepository.findByTenantId(tenantId)
                .stream()
                .filter(d -> isInPeriod(d.getCreatedAt(), from, to))
                .filter(d -> d.getSentimentLabel() != null)
                .toList();

        return docs.stream()
                .collect(Collectors.groupingBy(TicketDocument::getSentimentLabel, Collectors.counting()));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private boolean isInPeriod(Instant timestamp, Instant from, Instant to) {
        if (timestamp == null) {
            return false;
        }
        boolean afterFrom = (from == null) || !timestamp.isBefore(from);
        boolean beforeTo  = (to == null)   || timestamp.isBefore(to);
        return afterFrom && beforeTo;
    }
}
