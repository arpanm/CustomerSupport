package in.supporthub.tenant.dto;

import in.supporthub.tenant.domain.PlanType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request DTO for creating a new tenant.
 *
 * <p>All fields are validated before reaching the service layer.
 *
 * @param slug      URL-friendly unique identifier (e.g., {@code acme-corp}). Must be unique.
 * @param name      Human-readable display name for the tenant.
 * @param planType  Subscription plan tier — determines feature limits and SLA defaults.
 * @param timezone  IANA timezone identifier (e.g., {@code Asia/Kolkata}). Optional; defaults to {@code Asia/Kolkata}.
 * @param locale    BCP 47 locale tag (e.g., {@code en-IN}). Optional; defaults to {@code en-IN}.
 */
public record CreateTenantRequest(

        @NotBlank(message = "slug must not be blank")
        String slug,

        @NotBlank(message = "name must not be blank")
        String name,

        @NotNull(message = "planType must not be null")
        PlanType planType,

        String timezone,

        String locale
) {
}
