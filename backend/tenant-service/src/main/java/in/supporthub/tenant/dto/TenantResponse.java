package in.supporthub.tenant.dto;

import in.supporthub.tenant.domain.PlanType;
import in.supporthub.tenant.domain.TenantStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO for tenant information.
 *
 * @param id        Internal UUID of the tenant.
 * @param slug      URL-friendly unique identifier (e.g., {@code acme-corp}).
 * @param name      Human-readable display name.
 * @param planType  Subscription plan tier.
 * @param status    Current lifecycle status.
 * @param timezone  IANA timezone identifier (e.g., {@code Asia/Kolkata}).
 * @param locale    BCP 47 locale tag (e.g., {@code en-IN}).
 * @param createdAt Timestamp when the tenant was created.
 */
public record TenantResponse(
        UUID id,
        String slug,
        String name,
        PlanType planType,
        TenantStatus status,
        String timezone,
        String locale,
        Instant createdAt
) {
}
