package in.supporthub.reporting.dto;

/**
 * Ticket count grouped by category — used for category distribution charts.
 *
 * @param categoryId   UUID of the category.
 * @param categoryName Display name of the category (may be empty if not enriched).
 * @param count        Number of tickets in this category.
 */
public record CategoryCount(
        String categoryId,
        String categoryName,
        long count) {
}
