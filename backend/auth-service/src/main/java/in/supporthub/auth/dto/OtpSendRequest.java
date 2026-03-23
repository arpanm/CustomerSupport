package in.supporthub.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Request body for the OTP send endpoint.
 *
 * @param phone E.164 Indian mobile number (must start with +91 followed by 10 digits).
 */
public record OtpSendRequest(
        @NotBlank(message = "Phone number is required")
        @Pattern(
                regexp = "^\\+91[0-9]{10}$",
                message = "Phone must be a valid Indian mobile number in E.164 format (+91XXXXXXXXXX)")
        String phone
) {}
