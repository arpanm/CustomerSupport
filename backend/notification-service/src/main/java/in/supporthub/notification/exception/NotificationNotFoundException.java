package in.supporthub.notification.exception;

import in.supporthub.shared.exception.AppException;
import org.springframework.http.HttpStatus;

/**
 * Thrown when a requested notification document cannot be found.
 */
public class NotificationNotFoundException extends AppException {

    public NotificationNotFoundException(String notificationId) {
        super("NOTIFICATION_NOT_FOUND",
                "Notification not found: " + notificationId,
                HttpStatus.NOT_FOUND);
    }
}
