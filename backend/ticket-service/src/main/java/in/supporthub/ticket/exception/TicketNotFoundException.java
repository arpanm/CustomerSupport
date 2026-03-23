package in.supporthub.ticket.exception;

import in.supporthub.shared.exception.AppException;
import org.springframework.http.HttpStatus;

/**
 * Thrown when a ticket cannot be found by its ticket number within a tenant.
 */
public class TicketNotFoundException extends AppException {

    /**
     * @param ticketNumber the human-readable ticket number that was not found
     */
    public TicketNotFoundException(String ticketNumber) {
        super("TICKET_NOT_FOUND", "Ticket not found: " + ticketNumber, HttpStatus.NOT_FOUND);
    }
}
