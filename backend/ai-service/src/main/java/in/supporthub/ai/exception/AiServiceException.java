package in.supporthub.ai.exception;

import in.supporthub.shared.exception.AppException;
import org.springframework.http.HttpStatus;

/**
 * Exception thrown when an unrecoverable AI service error occurs.
 *
 * <p><b>Important:</b> AI failures (sentiment, resolution suggestions) are handled internally
 * with fallback values and should NEVER surface this exception to API clients. This exception
 * is reserved for programming errors or configuration failures that should fail fast.
 *
 * <p>Error code: {@code AI_SERVICE_ERROR} | HTTP status: 500 Internal Server Error.
 */
public class AiServiceException extends AppException {

    private static final String ERROR_CODE = "AI_SERVICE_ERROR";

    /**
     * Creates an AiServiceException with a descriptive message.
     *
     * @param message human-readable description of the error
     */
    public AiServiceException(String message) {
        super(ERROR_CODE, message, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    /**
     * Creates an AiServiceException with a descriptive message and a root cause.
     *
     * @param message human-readable description of the error
     * @param cause   the underlying exception
     */
    public AiServiceException(String message, Throwable cause) {
        super(ERROR_CODE, message, HttpStatus.INTERNAL_SERVER_ERROR, cause);
    }
}
