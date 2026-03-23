package in.supporthub.ticket.exception;

import in.supporthub.shared.exception.AppException;
import in.supporthub.ticket.domain.TicketStatus;
import org.springframework.http.HttpStatus;

/**
 * Thrown when a requested status transition is not permitted by the ticket state machine.
 *
 * <p>Returns HTTP 409 Conflict to signal that the current state of the ticket prevents
 * the requested operation.
 */
public class InvalidStatusTransitionException extends AppException {

    /**
     * @param currentStatus the status the ticket is currently in
     * @param targetStatus  the status that was requested
     */
    public InvalidStatusTransitionException(TicketStatus currentStatus, TicketStatus targetStatus) {
        super(
                "INVALID_STATUS_TRANSITION",
                String.format("Cannot transition ticket from %s to %s", currentStatus, targetStatus),
                HttpStatus.CONFLICT
        );
    }
}
