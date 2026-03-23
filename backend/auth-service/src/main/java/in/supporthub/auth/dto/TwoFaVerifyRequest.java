package in.supporthub.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for the agent 2-FA OTP verification endpoint.
 *
 * @param agentId UUID of the agent completing 2-FA (returned from the initial login call).
 * @param code    6-digit OTP sent to the agent's registered email address.
 */
public record TwoFaVerifyRequest(
        @NotBlank(message = "Agent ID is required")
        String agentId,

        @NotBlank(message = "2FA code is required")
        @Size(min = 6, max = 6, message = "2FA code must be exactly 6 digits")
        String code
) {}
