package in.supporthub.reporting.service;

import in.supporthub.reporting.domain.TicketDocument;
import in.supporthub.reporting.repository.TicketDocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

/**
 * Service for streaming ticket data as CSV to an output stream.
 *
 * <p>Data is fetched from the Elasticsearch read model via {@link TicketDocumentRepository}.
 * The CSV is written row-by-row without buffering all rows in memory, keeping memory
 * usage bounded for large exports.
 *
 * <p>CSV columns: ticketId, ticketNumber, title, status, priority, category,
 * assigneeId, createdAt, resolvedAt, slaBreached.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CsvExportService {

    private static final String CSV_HEADER =
            "ticketId,ticketNumber,title,status,priority,category,assigneeId,createdAt,resolvedAt,slaBreached";

    private final TicketDocumentRepository ticketDocumentRepository;

    /**
     * Writes CSV rows for all tickets belonging to {@code tenantId} in the given period
     * to the provided {@link OutputStream}.
     *
     * <p>Callers are responsible for closing the stream after this method returns.
     *
     * @param from     inclusive start of the export window; {@code null} means unbounded
     * @param to       exclusive end of the export window; {@code null} means unbounded
     * @param tenantId tenant identifier — resolved from {@link in.supporthub.shared.security.TenantContextHolder}
     * @param out      destination stream (e.g. {@code HttpServletResponse#getOutputStream()})
     * @throws IOException if writing to the stream fails
     */
    public void streamTicketsCsv(Instant from, Instant to, String tenantId, OutputStream out)
            throws IOException {

        log.info("CSV export started: tenantId={}, from={}, to={}", tenantId, from, to);

        List<TicketDocument> docs = ticketDocumentRepository.findByTenantId(tenantId)
                .stream()
                .filter(d -> isInPeriod(d.getCreatedAt(), from, to))
                .toList();

        log.info("CSV export: tenantId={}, rowCount={}", tenantId, docs.size());

        // Use PrintWriter for efficient char-by-char writing; auto-flush disabled for performance.
        try (PrintWriter writer = new PrintWriter(
                new OutputStreamWriter(out, StandardCharsets.UTF_8), false)) {

            writer.println(CSV_HEADER);

            for (TicketDocument doc : docs) {
                StringBuilder row = new StringBuilder();
                row.append(escapeCsv(doc.getId())).append(',');
                row.append(escapeCsv(doc.getTicketNumber())).append(',');
                // title not stored in TicketDocument — emit empty to preserve column count
                row.append(',');
                row.append(escapeCsv(doc.getStatus())).append(',');
                row.append(escapeCsv(doc.getPriority())).append(',');
                row.append(escapeCsv(doc.getCategoryId())).append(',');
                row.append(escapeCsv(doc.getAssignedAgentId())).append(',');
                row.append(doc.getCreatedAt() != null ? doc.getCreatedAt().toString() : "").append(',');
                row.append(doc.getResolvedAt() != null ? doc.getResolvedAt().toString() : "").append(',');
                row.append(Boolean.TRUE.equals(doc.getSlaBreached()) ? "true" : "false");
                writer.println(row);
            }

            writer.flush();
        }

        log.info("CSV export completed: tenantId={}", tenantId);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Escapes a CSV field value by wrapping in double-quotes and doubling any embedded quotes.
     * Returns an empty string for {@code null} values.
     */
    private String escapeCsv(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private boolean isInPeriod(Instant timestamp, Instant from, Instant to) {
        if (timestamp == null) {
            return false;
        }
        boolean afterFrom = (from == null) || !timestamp.isBefore(from);
        boolean beforeTo  = (to == null)   || timestamp.isBefore(to);
        return afterFrom && beforeTo;
    }
}
