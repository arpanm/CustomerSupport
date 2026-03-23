package in.supporthub.shared.exception;

import org.springframework.http.HttpStatus;

/**
 * Base exception for all SupportHub application errors.
 *
 * <p>Subclasses should provide a specific {@code errorCode} (e.g., "TICKET_NOT_FOUND") and a
 * meaningful {@code message} that is safe to return to the API caller.
 *
 * <p>Usage pattern:
 * <pre>{@code
 * public class TicketNotFoundException extends AppException {
 *     public TicketNotFoundException(String ticketNumber) {
 *         super("TICKET_NOT_FOUND", "Ticket not found: " + ticketNumber, HttpStatus.NOT_FOUND);
 *     }
 * }
 * }</pre>
 */
public class AppException extends RuntimeException {

    private final String errorCode;
    private final HttpStatus httpStatus;

    /**
     * Creates an AppException with an error code, human-readable message, and HTTP status.
     *
     * @param errorCode  Machine-readable code used in the {@code ApiError} response envelope
     *                   (e.g., "TICKET_NOT_FOUND", "TENANT_SUSPENDED").
     * @param message    Human-readable description of the error — safe to surface to API callers.
     * @param httpStatus HTTP status code to return in the response.
     */
    public AppException(String errorCode, String message, HttpStatus httpStatus) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }

    /**
     * Creates an AppException with a root cause.
     *
     * @param errorCode  Machine-readable error code.
     * @param message    Human-readable description.
     * @param httpStatus HTTP status code to return.
     * @param cause      Original exception that triggered this error.
     */
    public AppException(String errorCode, String message, HttpStatus httpStatus, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }
}
