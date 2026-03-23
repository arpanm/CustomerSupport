package in.supporthub.customer.dto;

import java.util.List;

/**
 * Simple offset-paginated response wrapper used for list endpoints in the customer-service.
 *
 * <p>For consistency with the rest of SupportHub, large datasets should use
 * {@link in.supporthub.shared.dto.PagedApiResponse} with cursor-based pagination.
 * This record is provided for smaller, bounded lists (e.g. order history pages).
 *
 * @param <T>   type of each item
 * @param data  list of items for the current page
 * @param page  zero-based page index
 * @param size  requested page size
 * @param total total item count across all pages
 */
public record PagedResponse<T>(
        List<T> data,
        int page,
        int size,
        long total
) {}
