package in.supporthub.customer.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for updating a customer's profile.
 *
 * <p>Phone number is immutable — it cannot be changed through this endpoint.
 * Only display name, preferred language, and timezone may be updated.
 *
 * <p>All fields are optional (null means no change). Validation only applies to
 * non-null values.
 *
 * @param displayName       new display name — max 150 characters; null to leave unchanged
 * @param preferredLanguage BCP-47 language tag — must be exactly 2 lowercase letters;
 *                          null to leave unchanged
 * @param timezone          IANA timezone identifier, e.g. "Asia/Kolkata"; null to leave unchanged
 */
public record UpdateProfileRequest(
        @Size(max = 150, message = "Display name must not exceed 150 characters")
        String displayName,

        @Pattern(regexp = "^[a-z]{2}$", message = "Preferred language must be a 2-letter lowercase BCP-47 code (e.g. 'en', 'hi')")
        String preferredLanguage,

        String timezone
) {}
