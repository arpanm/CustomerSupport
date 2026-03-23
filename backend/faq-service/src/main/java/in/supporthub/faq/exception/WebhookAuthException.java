package in.supporthub.faq.exception;

import in.supporthub.shared.exception.AppException;
import org.springframework.http.HttpStatus;

/**
 * Thrown when a Strapi webhook request fails HMAC-SHA256 signature validation.
 *
 * <p>Returns HTTP 401 to the caller so Strapi can detect and alert on signature failures.
 * Never log the received signature value to avoid leaking secret material.
 */
public class WebhookAuthException extends AppException {

    public WebhookAuthException() {
        super("WEBHOOK_AUTH_FAILED",
                "Strapi webhook signature validation failed",
                HttpStatus.UNAUTHORIZED);
    }

    public WebhookAuthException(String message) {
        super("WEBHOOK_AUTH_FAILED", message, HttpStatus.UNAUTHORIZED);
    }
}
