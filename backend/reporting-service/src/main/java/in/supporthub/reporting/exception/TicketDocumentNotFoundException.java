package in.supporthub.reporting.exception;

import in.supporthub.shared.exception.AppException;
import org.springframework.http.HttpStatus;

/**
 * Thrown when a {@link in.supporthub.reporting.domain.TicketDocument} cannot be found
 * in Elasticsearch for the given ticket ID and tenant.
 */
public class TicketDocumentNotFoundException extends AppException {

    public TicketDocumentNotFoundException(String ticketId, String tenantId) {
        super(
                "TICKET_DOCUMENT_NOT_FOUND",
                "Ticket document not found: ticketId=" + ticketId + ", tenantId=" + tenantId,
                HttpStatus.NOT_FOUND
        );
    }
}
