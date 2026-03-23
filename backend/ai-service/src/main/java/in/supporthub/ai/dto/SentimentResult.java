package in.supporthub.ai.dto;

/**
 * Result of a sentiment analysis operation.
 *
 * @param label  One of: "very_negative", "negative", "neutral", "positive", "very_positive".
 * @param score  Normalised sentiment score from -1.0 (very negative) to 1.0 (very positive).
 * @param reason Brief explanation (max 100 chars) of the sentiment classification.
 */
public record SentimentResult(
        String label,
        double score,
        String reason) {}
