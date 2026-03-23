package in.supporthub.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Request body for the agent login endpoint.
 *
 * @param email    Agent login email address.
 * @param password Agent plaintext password (transmitted over HTTPS; never logged or stored).
 */
public record AgentLoginRequest(
        @NotBlank(message = "Email is required")
        @Email(message = "Must be a valid email address")
        String email,

        @NotBlank(message = "Password is required")
        String password
) {}
