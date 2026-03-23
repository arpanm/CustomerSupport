package in.supporthub.faq.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for the self-resolution FAQ search endpoint.
 *
 * <p>Customers submit a natural-language query before creating a support ticket.
 * The service performs a semantic search (pgvector) with keyword fallback,
 * returning the most relevant FAQ entries.
 */
public record FaqSearchRequest(
        @NotBlank(message = "query must not be blank")
        String query,

        @Min(value = 1, message = "limit must be at least 1")
        @Max(value = 10, message = "limit must not exceed 10")
        int limit
) {
}
