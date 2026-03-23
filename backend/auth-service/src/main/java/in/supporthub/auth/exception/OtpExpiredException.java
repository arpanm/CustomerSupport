package in.supporthub.auth.exception;

import in.supporthub.shared.exception.AppException;
import org.springframework.http.HttpStatus;

/**
 * Thrown when an OTP verification attempt is made but the OTP has expired or does not exist
 * in Redis (TTL of 300 seconds has elapsed).
 */
public class OtpExpiredException extends AppException {

    public OtpExpiredException() {
        super(
                "OTP_EXPIRED",
                "The OTP has expired. Please request a new one.",
                HttpStatus.BAD_REQUEST
        );
    }
}
