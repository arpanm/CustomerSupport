package in.supporthub.reporting.controller;

import in.supporthub.reporting.dto.AgentMetrics;
import in.supporthub.reporting.dto.AgentPerformanceResult;
import in.supporthub.reporting.dto.CategoryCount;
import in.supporthub.reporting.dto.DashboardSummary;
import in.supporthub.reporting.dto.SlaComplianceResult;
import in.supporthub.reporting.dto.TrendPoint;
import in.supporthub.reporting.service.CsvExportService;
import in.supporthub.reporting.service.DashboardService;
import in.supporthub.shared.dto.ApiResponse;
import in.supporthub.shared.security.TenantContextHolder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * REST controller for the SupportHub reporting and analytics API.
 *
 * <p>All endpoints:
 * <ul>
 *   <li>Require a valid {@code X-Tenant-ID} header (set by the API gateway).</li>
 *   <li>Read the tenant from {@link TenantContextHolder} — never from request parameters.</li>
 *   <li>Are stateless and read-only (no write operations).</li>
 * </ul>
 *
 * <p>Role requirements:
 * <ul>
 *   <li>Most endpoints: {@code AGENT} or higher.</li>
 *   <li>Agent performance endpoint: {@code ADMIN} only.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/reports")
@Slf4j
@RequiredArgsConstructor
@Tag(name = "Reporting", description = "Dashboard metrics, ticket analytics, and agent performance reports")
public class ReportingController {

    private final DashboardService dashboardService;
    private final CsvExportService csvExportService;

    // -------------------------------------------------------------------------
    // Dashboard summary
    // -------------------------------------------------------------------------

    /**
     * Returns aggregated dashboard metrics for the authenticated tenant.
     *
     * @param from start of the reporting period (ISO-8601 instant)
     * @param to   end of the reporting period (ISO-8601 instant)
     * @return {@link DashboardSummary} with counts, averages, and breach rates
     */
    @GetMapping("/dashboard")
    @PreAuthorize("hasAnyRole('AGENT','ADMIN','SUPER_ADMIN')")
    @Operation(summary = "Get dashboard summary metrics")
    public ResponseEntity<ApiResponse<DashboardSummary>> getDashboardSummary(
            @Parameter(description = "Period start (ISO-8601)", example = "2026-01-01T00:00:00Z")
            @RequestParam(required = false) Instant from,
            @Parameter(description = "Period end (ISO-8601)", example = "2026-03-31T23:59:59Z")
            @RequestParam(required = false) Instant to) {

        String tenantId = TenantContextHolder.getTenantId();
        log.info("GET /dashboard: tenantId={}", tenantId);

        DashboardSummary summary = dashboardService.getDashboardSummary(tenantId, from, to);
        return ResponseEntity.ok(ApiResponse.of(summary));
    }

    // -------------------------------------------------------------------------
    // Ticket distribution endpoints
    // -------------------------------------------------------------------------

    /**
     * Returns ticket counts grouped by status for the authenticated tenant.
     *
     * @return map of status → count suitable for pie/donut charts
     */
    @GetMapping("/tickets/by-status")
    @PreAuthorize("hasAnyRole('AGENT','ADMIN','SUPER_ADMIN')")
    @Operation(summary = "Get ticket counts by status (pie chart data)")
    public ResponseEntity<ApiResponse<Map<String, Long>>> getTicketsByStatus() {
        String tenantId = TenantContextHolder.getTenantId();
        log.info("GET /tickets/by-status: tenantId={}", tenantId);

        Map<String, Long> result = dashboardService.getTicketsByStatus(tenantId);
        return ResponseEntity.ok(ApiResponse.of(result));
    }

    /**
     * Returns ticket counts grouped by category for the authenticated tenant and period.
     *
     * @param from start of the reporting period (optional)
     * @param to   end of the reporting period (optional)
     * @return list of {@link CategoryCount} sorted by count descending
     */
    @GetMapping("/tickets/by-category")
    @PreAuthorize("hasAnyRole('AGENT','ADMIN','SUPER_ADMIN')")
    @Operation(summary = "Get ticket counts by category")
    public ResponseEntity<ApiResponse<List<CategoryCount>>> getTicketsByCategory(
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to) {

        String tenantId = TenantContextHolder.getTenantId();
        log.info("GET /tickets/by-category: tenantId={}", tenantId);

        List<CategoryCount> result = dashboardService.getTicketsByCategory(tenantId, from, to);
        return ResponseEntity.ok(ApiResponse.of(result));
    }

    /**
     * Returns time-series ticket volume data for the authenticated tenant.
     *
     * @param from        start of the period (optional)
     * @param to          end of the period (optional)
     * @param granularity {@code "daily"} (default) or {@code "weekly"}
     * @return ordered list of {@link TrendPoint} data points
     */
    @GetMapping("/tickets/trend")
    @PreAuthorize("hasAnyRole('AGENT','ADMIN','SUPER_ADMIN')")
    @Operation(summary = "Get ticket volume trend (line chart data)")
    public ResponseEntity<ApiResponse<List<TrendPoint>>> getTicketTrend(
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "daily") String granularity) {

        String tenantId = TenantContextHolder.getTenantId();
        log.info("GET /tickets/trend: tenantId={}, granularity={}", tenantId, granularity);

        List<TrendPoint> result = dashboardService.getTicketTrend(tenantId, from, to, granularity);
        return ResponseEntity.ok(ApiResponse.of(result));
    }

    // -------------------------------------------------------------------------
    // Agent performance (ADMIN only)
    // -------------------------------------------------------------------------

    /**
     * Returns per-agent performance metrics for the authenticated tenant. ADMIN role required.
     *
     * @param from start of the period (optional)
     * @param to   end of the period (optional)
     * @return list of {@link AgentMetrics} sorted by resolved count descending
     */
    @GetMapping("/agents/performance")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    @Operation(summary = "Get agent performance metrics (ADMIN only)")
    public ResponseEntity<ApiResponse<List<AgentMetrics>>> getAgentPerformance(
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to) {

        String tenantId = TenantContextHolder.getTenantId();
        log.info("GET /agents/performance: tenantId={}", tenantId);

        List<AgentMetrics> result = dashboardService.getAgentPerformance(tenantId, from, to);
        return ResponseEntity.ok(ApiResponse.of(result));
    }

    // -------------------------------------------------------------------------
    // Sentiment distribution
    // -------------------------------------------------------------------------

    /**
     * Returns sentiment label distribution for the authenticated tenant.
     *
     * @param from start of the period (optional)
     * @param to   end of the period (optional)
     * @return map of sentimentLabel → count
     */
    @GetMapping("/sentiment")
    @PreAuthorize("hasAnyRole('AGENT','ADMIN','SUPER_ADMIN')")
    @Operation(summary = "Get sentiment distribution")
    public ResponseEntity<ApiResponse<Map<String, Long>>> getSentimentDistribution(
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to) {

        String tenantId = TenantContextHolder.getTenantId();
        log.info("GET /sentiment: tenantId={}", tenantId);

        Map<String, Long> result = dashboardService.getSentimentDistribution(tenantId, from, to);
        return ResponseEntity.ok(ApiResponse.of(result));
    }

    // -------------------------------------------------------------------------
    // CSV export (FEAT-029)
    // -------------------------------------------------------------------------

    /**
     * Streams all tickets for the authenticated tenant in the given date range as a CSV file.
     *
     * <p>Sets {@code Content-Disposition: attachment; filename="tickets-export.csv"} and
     * {@code Content-Type: text/csv} so that browsers trigger a file download.
     *
     * @param from     start of the export period (ISO-8601 instant, optional)
     * @param to       end of the export period (ISO-8601 instant, optional)
     * @param response HTTP response used to write the CSV stream
     * @throws IOException if writing to the response output stream fails
     */
    @GetMapping("/export")
    @PreAuthorize("hasAnyRole('AGENT','ADMIN','SUPER_ADMIN')")
    @Operation(summary = "Stream tickets as CSV attachment")
    public void exportTicketsCsv(
            @Parameter(description = "Period start (ISO-8601)", example = "2026-01-01T00:00:00Z")
            @RequestParam(required = false) Instant from,
            @Parameter(description = "Period end (ISO-8601)", example = "2026-03-31T23:59:59Z")
            @RequestParam(required = false) Instant to,
            HttpServletResponse response) throws IOException {

        String tenantId = TenantContextHolder.getTenantId();
        log.info("GET /export: tenantId={}, from={}, to={}", tenantId, from, to);

        response.setContentType(MediaType.TEXT_PLAIN_VALUE + ";charset=UTF-8");
        response.setHeader(HttpHeaders.CONTENT_TYPE, "text/csv; charset=UTF-8");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"tickets-export.csv\"");

        csvExportService.streamTicketsCsv(from, to, tenantId, response.getOutputStream());
    }

    // -------------------------------------------------------------------------
    // SLA compliance (FEAT-029)
    // -------------------------------------------------------------------------

    /**
     * Returns SLA compliance statistics per ticket category for the authenticated tenant.
     *
     * @param from start of the reporting period (optional)
     * @param to   end of the reporting period (optional)
     * @return list of {@link SlaComplianceResult} sorted by category name ascending
     */
    @GetMapping("/sla-compliance")
    @PreAuthorize("hasAnyRole('AGENT','ADMIN','SUPER_ADMIN')")
    @Operation(summary = "Get SLA compliance per ticket category")
    public ResponseEntity<ApiResponse<List<SlaComplianceResult>>> getSlaCompliance(
            @Parameter(description = "Period start (ISO-8601)", example = "2026-01-01T00:00:00Z")
            @RequestParam(required = false) Instant from,
            @Parameter(description = "Period end (ISO-8601)", example = "2026-03-31T23:59:59Z")
            @RequestParam(required = false) Instant to) {

        String tenantId = TenantContextHolder.getTenantId();
        log.info("GET /sla-compliance: tenantId={}", tenantId);

        List<SlaComplianceResult> result = dashboardService.getSlaCompliance(from, to, tenantId);
        return ResponseEntity.ok(ApiResponse.of(result));
    }

    // -------------------------------------------------------------------------
    // Agent performance — extended (FEAT-029)
    // -------------------------------------------------------------------------

    /**
     * Returns extended per-agent performance metrics for the authenticated tenant. ADMIN role required.
     *
     * @param from start of the period (optional)
     * @param to   end of the period (optional)
     * @return list of {@link AgentPerformanceResult} sorted by ticketsResolved descending
     */
    @GetMapping("/agent-performance")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    @Operation(summary = "Get extended agent performance metrics (ADMIN only)")
    public ResponseEntity<ApiResponse<List<AgentPerformanceResult>>> getAgentPerformanceResults(
            @Parameter(description = "Period start (ISO-8601)", example = "2026-01-01T00:00:00Z")
            @RequestParam(required = false) Instant from,
            @Parameter(description = "Period end (ISO-8601)", example = "2026-03-31T23:59:59Z")
            @RequestParam(required = false) Instant to) {

        String tenantId = TenantContextHolder.getTenantId();
        log.info("GET /agent-performance: tenantId={}", tenantId);

        List<AgentPerformanceResult> result =
                dashboardService.getAgentPerformanceResults(from, to, tenantId);
        return ResponseEntity.ok(ApiResponse.of(result));
    }
}
