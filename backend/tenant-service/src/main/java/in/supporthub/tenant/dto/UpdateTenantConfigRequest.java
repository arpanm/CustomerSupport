package in.supporthub.tenant.dto;

import java.util.Map;

/**
 * Request DTO for upserting tenant configuration key-value pairs.
 *
 * <p>Each entry in {@code configs} will be inserted if the key does not exist
 * or updated if it already exists for the tenant.
 *
 * @param configs Map of configuration key-value pairs to upsert. Keys such as
 *                {@code branding.primary_color} or {@code sla.default_response_hours}.
 */
public record UpdateTenantConfigRequest(
        Map<String, String> configs
) {
}
