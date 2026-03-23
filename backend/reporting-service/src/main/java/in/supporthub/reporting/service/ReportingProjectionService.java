package in.supporthub.reporting.service;

import in.supporthub.reporting.domain.TicketDocument;
import in.supporthub.reporting.exception.TicketDocumentNotFoundException;
import in.supporthub.reporting.repository.TicketDocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Service responsible for maintaining the Elasticsearch read model (CQRS projection).
 *
 * <p>All mutating operations call this service from the Kafka consumers. It handles
 * upserts with basic retry logic for version conflicts.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ReportingProjectionService {

    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final String RESOLVED_STATUS = "RESOLVED";

    private final TicketDocumentRepository ticketDocumentRepository;

    /**
     * Saves or replaces a {@link TicketDocument} in Elasticsearch.
     *
     * <p>Retries up to {@value MAX_RETRY_ATTEMPTS} times on conflict.
     *
     * @param doc the ticket document to persist
     */
    public void upsertTicket(TicketDocument doc) {
        int attempt = 0;
        while (attempt < MAX_RETRY_ATTEMPTS) {
            try {
                ticketDocumentRepository.save(doc);
                log.debug("Ticket document upserted: id={}, tenantId={}, status={}",
                        doc.getId(), doc.getTenantId(), doc.getStatus());
                return;
            } catch (Exception ex) {
                attempt++;
                if (attempt >= MAX_RETRY_ATTEMPTS) {
                    log.error("Failed to upsert ticket document after {} attempts: id={}, tenantId={}, error={}",
                            MAX_RETRY_ATTEMPTS, doc.getId(), doc.getTenantId(), ex.getMessage(), ex);
                    throw ex;
                }
                log.warn("Retry {}/{} for upsert ticket document: id={}, error={}",
                        attempt, MAX_RETRY_ATTEMPTS, doc.getId(), ex.getMessage());
            }
        }
    }

    /**
     * Updates the status (and optionally resolution data) of an existing ticket document.
     *
     * <p>If {@code newStatus} is {@code "RESOLVED"}, {@code resolvedAt} is set to
     * {@code occurredAt} and {@code resolutionTimeMinutes} is computed.
     *
     * @param tenantId        tenant identifier
     * @param ticketId        ticket UUID
     * @param newStatus       the new ticket status string
     * @param assignedAgentId the currently assigned agent UUID (may be null)
     * @param occurredAt      when the status change occurred
     */
    public void updateTicketStatus(
            String tenantId,
            String ticketId,
            String newStatus,
            String assignedAgentId,
            Instant occurredAt) {

        Optional<TicketDocument> optionalDoc = ticketDocumentRepository.findById(ticketId);

        if (optionalDoc.isEmpty()) {
            log.warn("Cannot update status — ticket document not found: ticketId={}, tenantId={}",
                    ticketId, tenantId);
            return;
        }

        TicketDocument doc = optionalDoc.get();

        if (!tenantId.equals(doc.getTenantId())) {
            log.error("Tenant mismatch on status update: ticketId={}, expected tenantId={}, actual={}",
                    ticketId, tenantId, doc.getTenantId());
            return;
        }

        doc.setStatus(newStatus);
        doc.setUpdatedAt(Instant.now());

        if (assignedAgentId != null && !assignedAgentId.isBlank()) {
            doc.setAssignedAgentId(assignedAgentId);
        }

        if (RESOLVED_STATUS.equalsIgnoreCase(newStatus)) {
            doc.setResolvedAt(occurredAt);
            if (doc.getCreatedAt() != null) {
                long minutes = Duration.between(doc.getCreatedAt(), occurredAt).toMinutes();
                doc.setResolutionTimeMinutes(minutes);
            }
        }

        upsertTicket(doc);

        log.info("Ticket document status updated: ticketId={}, tenantId={}, newStatus={}",
                ticketId, tenantId, newStatus);
    }

    /**
     * Updates the sentiment label and score on an existing ticket document.
     *
     * @param tenantId  tenant identifier
     * @param ticketId  ticket UUID
     * @param label     sentiment label (e.g., "positive", "negative")
     * @param score     normalised sentiment score in [-1.0, 1.0]
     */
    public void updateSentiment(String tenantId, String ticketId, String label, double score) {
        Optional<TicketDocument> optionalDoc = ticketDocumentRepository.findById(ticketId);

        if (optionalDoc.isEmpty()) {
            log.warn("Cannot update sentiment — ticket document not found: ticketId={}, tenantId={}",
                    ticketId, tenantId);
            return;
        }

        TicketDocument doc = optionalDoc.get();

        if (!tenantId.equals(doc.getTenantId())) {
            log.error("Tenant mismatch on sentiment update: ticketId={}, expected tenantId={}, actual={}",
                    ticketId, tenantId, doc.getTenantId());
            return;
        }

        doc.setSentimentLabel(label);
        doc.setSentimentScore(score);
        doc.setUpdatedAt(Instant.now());

        upsertTicket(doc);

        log.info("Ticket document sentiment updated: ticketId={}, tenantId={}, label={}",
                ticketId, tenantId, label);
    }
}
