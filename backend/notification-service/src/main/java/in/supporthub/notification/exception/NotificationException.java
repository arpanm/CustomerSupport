package in.supporthub.notification.exception;

import in.supporthub.shared.exception.AppException;
import org.springframework.http.HttpStatus;

/**
 * Thrown when a notification operation fails due to a business rule violation.
 */
public class NotificationException extends AppException {

    public NotificationException(String message) {
        super("NOTIFICATION_ERROR", message, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    public NotificationException(String message, Throwable cause) {
        super("NOTIFICATION_ERROR", message, HttpStatus.INTERNAL_SERVER_ERROR, cause);
    }
}
