package in.supporthub.tenant.dto;

import java.util.Map;
import java.util.UUID;

/**
 * Response DTO for tenant configuration entries.
 *
 * @param tenantId UUID of the tenant owning the configuration.
 * @param configs  Map of all configuration key-value pairs for the tenant.
 */
public record TenantConfigResponse(
        UUID tenantId,
        Map<String, String> configs
) {
}
