package in.supporthub.ticket.domain;

/**
 * AI-computed sentiment classification for a ticket's text.
 *
 * <p>Maps to sentiment scores:
 * <ul>
 *   <li>VERY_NEGATIVE: score in [-1.0, -0.6)</li>
 *   <li>NEGATIVE:      score in [-0.6, -0.2)</li>
 *   <li>NEUTRAL:       score in [-0.2,  0.2]</li>
 *   <li>POSITIVE:      score in  (0.2,  0.6]</li>
 *   <li>VERY_POSITIVE: score in  (0.6,  1.0]</li>
 * </ul>
 */
public enum SentimentLabel {
    VERY_NEGATIVE,
    NEGATIVE,
    NEUTRAL,
    POSITIVE,
    VERY_POSITIVE
}
