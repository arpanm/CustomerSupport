package in.supporthub.auth.exception;

import in.supporthub.shared.exception.AppException;
import org.springframework.http.HttpStatus;

/**
 * Thrown when an OTP verification attempt is made with an incorrect code.
 */
public class OtpInvalidException extends AppException {

    public OtpInvalidException() {
        super(
                "OTP_INVALID",
                "Invalid OTP. Please check the code and try again.",
                HttpStatus.BAD_REQUEST
        );
    }
}
