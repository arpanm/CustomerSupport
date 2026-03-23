package in.supporthub.faq.dto;

import java.util.UUID;

/**
 * A single result from a semantic or keyword FAQ search.
 *
 * <p>{@code answerExcerpt} contains at most 300 characters of the answer to keep
 * response payloads compact. Callers should follow up with a GET /faqs/{id} request
 * to retrieve the full answer.
 *
 * <p>{@code similarityScore} is the cosine similarity score in [0, 1].
 * For keyword fallback results it is set to {@code 0.0} to signal that no
 * vector similarity was computed.
 */
public record FaqSearchResult(
        UUID id,
        String question,
        String answerExcerpt,
        double similarityScore
) {
}
