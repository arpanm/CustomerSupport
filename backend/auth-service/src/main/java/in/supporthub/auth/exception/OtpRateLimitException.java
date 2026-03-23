package in.supporthub.auth.exception;

import in.supporthub.shared.exception.AppException;
import org.springframework.http.HttpStatus;

/**
 * Thrown when a phone number has exceeded the OTP send rate limit (3 sends per hour).
 */
public class OtpRateLimitException extends AppException {

    public OtpRateLimitException() {
        super(
                "OTP_RATE_LIMIT_EXCEEDED",
                "Too many OTP requests. Please try again after some time.",
                HttpStatus.TOO_MANY_REQUESTS
        );
    }
}
