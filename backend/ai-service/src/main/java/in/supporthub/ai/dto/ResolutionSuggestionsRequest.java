package in.supporthub.ai.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for resolution suggestions from the agent dashboard.
 *
 * @param ticketId     UUID of the ticket requiring resolution suggestions.
 * @param title        Ticket title.
 * @param description  Ticket description. PII will be stripped before processing.
 * @param categorySlug Slug of the ticket category (e.g., "order-not-delivered").
 */
public record ResolutionSuggestionsRequest(
        @NotBlank String ticketId,
        String title,
        String description,
        String categorySlug) {}
