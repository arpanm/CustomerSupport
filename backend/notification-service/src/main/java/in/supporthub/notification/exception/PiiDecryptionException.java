package in.supporthub.notification.exception;

import in.supporthub.shared.exception.AppException;
import org.springframework.http.HttpStatus;

/**
 * Thrown when AES-GCM decryption of a PII field fails.
 */
public class PiiDecryptionException extends AppException {

    public PiiDecryptionException(String message) {
        super("PII_DECRYPTION_FAILED", message, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    public PiiDecryptionException(String message, Throwable cause) {
        super("PII_DECRYPTION_FAILED", message, HttpStatus.INTERNAL_SERVER_ERROR, cause);
    }
}
