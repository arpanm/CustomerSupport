package in.supporthub.reporting.dto;

/**
 * A single data point in a time-series ticket volume trend.
 *
 * @param date  ISO-8601 date string representing the bucket label (e.g., "2026-03-23" for daily,
 *              "2026-W12" for weekly).
 * @param count Number of tickets in this time bucket.
 */
public record TrendPoint(
        String date,
        long count) {
}
