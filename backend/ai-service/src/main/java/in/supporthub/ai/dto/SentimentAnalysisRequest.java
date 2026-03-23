package in.supporthub.ai.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for on-demand sentiment analysis.
 *
 * @param ticketId UUID of the ticket being analysed.
 * @param text     Raw text to analyse (title + description). PII will be stripped before processing.
 */
public record SentimentAnalysisRequest(
        @NotBlank String ticketId,
        @NotBlank String text) {}
