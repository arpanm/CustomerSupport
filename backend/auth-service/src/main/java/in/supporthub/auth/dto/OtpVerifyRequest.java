package in.supporthub.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request body for the OTP verification endpoint.
 *
 * @param phone E.164 Indian mobile number used when requesting the OTP.
 * @param otp   6-digit one-time password received via SMS.
 */
public record OtpVerifyRequest(
        @NotBlank(message = "Phone number is required")
        @Pattern(
                regexp = "^\\+91[0-9]{10}$",
                message = "Phone must be a valid Indian mobile number in E.164 format (+91XXXXXXXXXX)")
        String phone,

        @NotBlank(message = "OTP is required")
        @Size(min = 6, max = 6, message = "OTP must be exactly 6 digits")
        String otp
) {}
