package in.supporthub.reporting.service;

import in.supporthub.reporting.domain.TicketDocument;
import in.supporthub.reporting.dto.AgentMetrics;
import in.supporthub.reporting.dto.CategoryCount;
import in.supporthub.reporting.dto.DashboardSummary;
import in.supporthub.reporting.dto.TrendPoint;
import in.supporthub.reporting.repository.TicketDocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DashboardService}.
 *
 * <p>All Elasticsearch interactions are mocked via {@link TicketDocumentRepository}.
 */
@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock
    private TicketDocumentRepository ticketDocumentRepository;

    @InjectMocks
    private DashboardService dashboardService;

    private static final String TENANT_ID = "tenant-abc-123";

    private Instant now;
    private Instant from;
    private Instant to;

    @BeforeEach
    void setUp() {
        now = Instant.parse("2026-03-23T12:00:00Z");
        from = now.minus(30, ChronoUnit.DAYS);
        to = now;
    }

    // -------------------------------------------------------------------------
    // getDashboardSummary
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("getDashboardSummary - returns correct total, open, and resolved counts")
    void getDashboardSummary_returnsCorrectCounts() {
        List<TicketDocument> docs = List.of(
                buildDoc("t1", "OPEN", false, null, 60L),
                buildDoc("t2", "OPEN", false, null, null),
                buildDoc("t3", "RESOLVED", false, now.minus(5, ChronoUnit.DAYS), 120L),
                buildDoc("t4", "RESOLVED", true,  now.minus(3, ChronoUnit.DAYS), 240L),
                buildDoc("t5", "IN_PROGRESS", false, null, null)
        );

        when(ticketDocumentRepository.findByTenantId(TENANT_ID)).thenReturn(docs);

        DashboardSummary summary = dashboardService.getDashboardSummary(TENANT_ID, from, to);

        assertThat(summary.totalTickets()).isEqualTo(5);
        assertThat(summary.openTickets()).isEqualTo(2);
        assertThat(summary.resolvedTickets()).isEqualTo(2);
    }

    @Test
    @DisplayName("getDashboardSummary - avgResolutionTime is calculated correctly")
    void getDashboardSummary_avgResolutionTimeCalculated() {
        List<TicketDocument> docs = List.of(
                buildDoc("t1", "RESOLVED", false, now.minus(5, ChronoUnit.DAYS), 60L),
                buildDoc("t2", "RESOLVED", false, now.minus(3, ChronoUnit.DAYS), 180L),
                buildDoc("t3", "OPEN", false, null, null)
        );

        when(ticketDocumentRepository.findByTenantId(TENANT_ID)).thenReturn(docs);

        DashboardSummary summary = dashboardService.getDashboardSummary(TENANT_ID, from, to);

        // avg of 60 and 180 = 120
        assertThat(summary.avgResolutionTimeMinutes()).isEqualTo(120.0, offset(0.001));
    }

    @Test
    @DisplayName("getDashboardSummary - slaBreachRate is slaBreachCount / total")
    void getDashboardSummary_slaBreachRateCalculated() {
        List<TicketDocument> docs = List.of(
                buildDoc("t1", "OPEN", false, null, null),
                buildDoc("t2", "OPEN", true,  null, null),
                buildDoc("t3", "RESOLVED", true, now.minus(2, ChronoUnit.DAYS), 90L),
                buildDoc("t4", "RESOLVED", false, now.minus(1, ChronoUnit.DAYS), 50L)
        );

        when(ticketDocumentRepository.findByTenantId(TENANT_ID)).thenReturn(docs);

        DashboardSummary summary = dashboardService.getDashboardSummary(TENANT_ID, from, to);

        assertThat(summary.slaBreachCount()).isEqualTo(2);
        // 2 breaches / 4 total = 0.5
        assertThat(summary.slaBreachRate()).isEqualTo(0.5, offset(0.001));
    }

    @Test
    @DisplayName("getDashboardSummary - slaBreachRate is 0.0 when there are no tickets")
    void getDashboardSummary_emptyTenant_returnsZeros() {
        when(ticketDocumentRepository.findByTenantId(TENANT_ID)).thenReturn(List.of());

        DashboardSummary summary = dashboardService.getDashboardSummary(TENANT_ID, from, to);

        assertThat(summary.totalTickets()).isZero();
        assertThat(summary.slaBreachRate()).isEqualTo(0.0, offset(0.001));
        assertThat(summary.avgResolutionTimeMinutes()).isEqualTo(0.0, offset(0.001));
    }

    @Test
    @DisplayName("getDashboardSummary - period filtering excludes tickets outside the range")
    void getDashboardSummary_filtersPeriodCorrectly() {
        TicketDocument insidePeriod = buildDoc("t1", "OPEN", false, null, null);
        insidePeriod.setCreatedAt(now.minus(10, ChronoUnit.DAYS));

        TicketDocument outsidePeriod = buildDoc("t2", "OPEN", false, null, null);
        outsidePeriod.setCreatedAt(now.minus(60, ChronoUnit.DAYS)); // before 'from'

        when(ticketDocumentRepository.findByTenantId(TENANT_ID))
                .thenReturn(List.of(insidePeriod, outsidePeriod));

        DashboardSummary summary = dashboardService.getDashboardSummary(TENANT_ID, from, to);

        assertThat(summary.totalTickets()).isEqualTo(1);
    }

    // -------------------------------------------------------------------------
    // getTicketsByStatus
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("getTicketsByStatus - groups tickets correctly")
    void getTicketsByStatus_groupsCorrectly() {
        List<TicketDocument> docs = List.of(
                buildDoc("t1", "OPEN", false, null, null),
                buildDoc("t2", "OPEN", false, null, null),
                buildDoc("t3", "RESOLVED", false, now.minus(1, ChronoUnit.DAYS), 30L),
                buildDoc("t4", "IN_PROGRESS", false, null, null)
        );

        when(ticketDocumentRepository.findByTenantId(TENANT_ID)).thenReturn(docs);

        Map<String, Long> result = dashboardService.getTicketsByStatus(TENANT_ID);

        assertThat(result).containsEntry("OPEN", 2L);
        assertThat(result).containsEntry("RESOLVED", 1L);
        assertThat(result).containsEntry("IN_PROGRESS", 1L);
    }

    // -------------------------------------------------------------------------
    // getTicketsByCategory
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("getTicketsByCategory - returns sorted category counts")
    void getTicketsByCategory_sortedByCountDesc() {
        TicketDocument d1 = buildDoc("t1", "OPEN", false, null, null);
        d1.setCategoryId("cat-billing");
        TicketDocument d2 = buildDoc("t2", "OPEN", false, null, null);
        d2.setCategoryId("cat-billing");
        TicketDocument d3 = buildDoc("t3", "OPEN", false, null, null);
        d3.setCategoryId("cat-shipping");

        when(ticketDocumentRepository.findByTenantId(TENANT_ID))
                .thenReturn(List.of(d1, d2, d3));

        List<CategoryCount> result = dashboardService.getTicketsByCategory(TENANT_ID, from, to);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).categoryId()).isEqualTo("cat-billing");
        assertThat(result.get(0).count()).isEqualTo(2);
        assertThat(result.get(1).categoryId()).isEqualTo("cat-shipping");
        assertThat(result.get(1).count()).isEqualTo(1);
    }

    // -------------------------------------------------------------------------
    // getTicketTrend
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("getTicketTrend - daily granularity produces one point per day")
    void getTicketTrend_daily_correctBuckets() {
        TicketDocument d1 = buildDoc("t1", "OPEN", false, null, null);
        d1.setCreatedAt(Instant.parse("2026-03-01T10:00:00Z"));

        TicketDocument d2 = buildDoc("t2", "OPEN", false, null, null);
        d2.setCreatedAt(Instant.parse("2026-03-01T15:00:00Z"));

        TicketDocument d3 = buildDoc("t3", "OPEN", false, null, null);
        d3.setCreatedAt(Instant.parse("2026-03-02T09:00:00Z"));

        Instant testFrom = Instant.parse("2026-03-01T00:00:00Z");
        Instant testTo   = Instant.parse("2026-03-03T00:00:00Z");

        when(ticketDocumentRepository.findByTenantId(TENANT_ID))
                .thenReturn(List.of(d1, d2, d3));

        List<TrendPoint> result = dashboardService.getTicketTrend(TENANT_ID, testFrom, testTo, "daily");

        assertThat(result).hasSize(2);
        assertThat(result.get(0).date()).isEqualTo("2026-03-01");
        assertThat(result.get(0).count()).isEqualTo(2);
        assertThat(result.get(1).date()).isEqualTo("2026-03-02");
        assertThat(result.get(1).count()).isEqualTo(1);
    }

    // -------------------------------------------------------------------------
    // getAgentPerformance
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("getAgentPerformance - computes per-agent metrics correctly")
    void getAgentPerformance_correctMetrics() {
        TicketDocument d1 = buildDoc("t1", "RESOLVED", false, now.minus(2, ChronoUnit.DAYS), 60L);
        d1.setAssignedAgentId("agent-1");

        TicketDocument d2 = buildDoc("t2", "OPEN", false, null, null);
        d2.setAssignedAgentId("agent-1");

        TicketDocument d3 = buildDoc("t3", "RESOLVED", false, now.minus(1, ChronoUnit.DAYS), 120L);
        d3.setAssignedAgentId("agent-2");

        when(ticketDocumentRepository.findByTenantId(TENANT_ID))
                .thenReturn(List.of(d1, d2, d3));

        List<AgentMetrics> result = dashboardService.getAgentPerformance(TENANT_ID, from, to);

        assertThat(result).hasSize(2);

        AgentMetrics agent2Metrics = result.stream()
                .filter(m -> "agent-2".equals(m.agentId()))
                .findFirst()
                .orElseThrow();

        assertThat(agent2Metrics.assignedCount()).isEqualTo(1);
        assertThat(agent2Metrics.resolvedCount()).isEqualTo(1);
        assertThat(agent2Metrics.avgResolutionMinutes()).isEqualTo(120.0, offset(0.001));

        AgentMetrics agent1Metrics = result.stream()
                .filter(m -> "agent-1".equals(m.agentId()))
                .findFirst()
                .orElseThrow();

        assertThat(agent1Metrics.assignedCount()).isEqualTo(2);
        assertThat(agent1Metrics.resolvedCount()).isEqualTo(1);
        assertThat(agent1Metrics.avgResolutionMinutes()).isEqualTo(60.0, offset(0.001));
    }

    // -------------------------------------------------------------------------
    // getSentimentDistribution
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("getSentimentDistribution - groups by sentiment label correctly")
    void getSentimentDistribution_groupsByLabel() {
        TicketDocument d1 = buildDoc("t1", "OPEN", false, null, null);
        d1.setSentimentLabel("positive");

        TicketDocument d2 = buildDoc("t2", "OPEN", false, null, null);
        d2.setSentimentLabel("negative");

        TicketDocument d3 = buildDoc("t3", "RESOLVED", false, now.minus(1, ChronoUnit.DAYS), 60L);
        d3.setSentimentLabel("positive");

        TicketDocument d4 = buildDoc("t4", "OPEN", false, null, null);
        // no sentiment set — should be excluded

        when(ticketDocumentRepository.findByTenantId(TENANT_ID))
                .thenReturn(List.of(d1, d2, d3, d4));

        Map<String, Long> result = dashboardService.getSentimentDistribution(TENANT_ID, from, to);

        assertThat(result).containsEntry("positive", 2L);
        assertThat(result).containsEntry("negative", 1L);
        assertThat(result).doesNotContainKey(null);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Builds a minimal {@link TicketDocument} with the given parameters.
     * The {@code createdAt} field is defaulted to 10 days before {@code now} so
     * that it falls within the default test period.
     */
    private TicketDocument buildDoc(
            String id,
            String status,
            boolean slaBreached,
            Instant resolvedAt,
            Long resolutionTimeMinutes) {

        TicketDocument doc = new TicketDocument();
        doc.setId(id);
        doc.setTenantId(TENANT_ID);
        doc.setStatus(status);
        doc.setSlaBreached(slaBreached);
        doc.setCreatedAt(now.minus(10, ChronoUnit.DAYS));
        doc.setResolvedAt(resolvedAt);
        doc.setResolutionTimeMinutes(resolutionTimeMinutes);
        doc.setUpdatedAt(now);
        return doc;
    }
}
