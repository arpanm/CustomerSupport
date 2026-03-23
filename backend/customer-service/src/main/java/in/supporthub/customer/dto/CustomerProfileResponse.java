package in.supporthub.customer.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO for a customer's profile.
 *
 * <p>PII policy: this record does NOT include phone, email, or raw display name in logs.
 * The caller must never log the {@code displayName} field.
 *
 * @param id                customer UUID
 * @param tenantId          owning tenant UUID
 * @param displayName       the customer's preferred display name (PII — do not log)
 * @param preferredLanguage BCP-47 language tag, e.g. "en", "hi"
 * @param timezone          IANA timezone identifier, e.g. "Asia/Kolkata"
 * @param isActive          whether the customer account is active
 * @param createdAt         timestamp when the customer was first registered
 */
public record CustomerProfileResponse(
        UUID id,
        UUID tenantId,
        String displayName,
        String preferredLanguage,
        String timezone,
        boolean isActive,
        Instant createdAt
) {}
