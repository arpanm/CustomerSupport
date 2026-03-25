package in.supporthub.tenant.dto;

import in.supporthub.tenant.domain.TenantStatus;
import jakarta.validation.constraints.NotNull;

/**
 * Request DTO for updating the lifecycle status of a tenant.
 *
 * @param status The new status to apply — must not be null.
 */
public record UpdateTenantStatusRequest(
        @NotNull(message = "status must not be null")
        TenantStatus status
) {
}
