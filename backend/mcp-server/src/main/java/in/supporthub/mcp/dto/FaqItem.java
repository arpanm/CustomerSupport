package in.supporthub.mcp.dto;

/**
 * A single FAQ result entry, used inside {@link FaqSearchResult}.
 *
 * @param question       the FAQ question text
 * @param answerExcerpt  a short excerpt of the answer (first ~200 characters)
 * @param relevanceScore cosine similarity score from the semantic search (0.0 – 1.0)
 */
public record FaqItem(
        String question,
        String answerExcerpt,
        double relevanceScore) {
}
