package in.supporthub.faq.exception;

import in.supporthub.shared.exception.AppException;
import org.springframework.http.HttpStatus;

import java.util.UUID;

/**
 * Thrown when a requested FAQ entry does not exist or does not belong to the current tenant.
 */
public class FaqNotFoundException extends AppException {

    public FaqNotFoundException(UUID id) {
        super("FAQ_NOT_FOUND", "FAQ entry not found: " + id, HttpStatus.NOT_FOUND);
    }

    public FaqNotFoundException(String message) {
        super("FAQ_NOT_FOUND", message, HttpStatus.NOT_FOUND);
    }
}
