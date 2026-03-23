package in.supporthub.ordersync.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for creating or updating an OMS configuration for a tenant.
 *
 * @param omsBaseUrl  Base URL of the OMS REST API (e.g., "https://oms.example.com/api/v1").
 *                    Must not be blank.
 * @param apiKey      Plaintext API key — will be encrypted before persistence.
 *                    Must not be blank.
 * @param authType    Authentication type: BEARER, API_KEY, or BASIC.
 *                    Defaults to BEARER if null.
 * @param headerName  HTTP header name to use for the API key
 *                    (e.g., "Authorization", "X-API-Key").
 *                    Defaults to "Authorization" if null.
 */
public record OmsConfigRequest(
        @NotBlank String omsBaseUrl,
        @NotBlank String apiKey,
        String authType,
        String headerName
) {
}
