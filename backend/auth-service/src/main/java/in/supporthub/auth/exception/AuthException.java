package in.supporthub.auth.exception;

import in.supporthub.shared.exception.AppException;
import org.springframework.http.HttpStatus;

/**
 * Thrown when authentication fails — invalid credentials, inactive account, etc.
 *
 * <p>The message is intentionally generic to avoid leaking whether an account exists.
 */
public class AuthException extends AppException {

    public AuthException(String message) {
        super("AUTHENTICATION_FAILED", message, HttpStatus.UNAUTHORIZED);
    }

    public AuthException() {
        super(
                "AUTHENTICATION_FAILED",
                "Authentication failed. Please check your credentials.",
                HttpStatus.UNAUTHORIZED
        );
    }
}
