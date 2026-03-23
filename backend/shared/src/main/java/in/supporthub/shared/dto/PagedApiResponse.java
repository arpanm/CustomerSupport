package in.supporthub.shared.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Generic success response envelope for paginated API responses.
 *
 * <p>All list/search endpoints that return multiple items MUST use this envelope.
 * Pagination uses cursor-based pagination — NOT offset pagination.
 *
 * <p>Example JSON:
 * <pre>{@code
 * {
 *   "data": [ ... ],
 *   "pagination": {
 *     "cursor": "base64encodedcursor==",
 *     "hasMore": true,
 *     "limit": 25,
 *     "total": 1234
 *   },
 *   "meta": {
 *     "requestId": "uuid",
 *     "timestamp": "2026-03-23T10:00:00Z",
 *     "apiVersion": "v1"
 *   }
 * }
 * }</pre>
 *
 * @param <T>        Type of each item in the list.
 * @param data       List of payload items for the current page.
 * @param pagination Cursor-based pagination metadata.
 * @param meta       Standard response metadata.
 */
public record PagedApiResponse<T>(List<T> data, Pagination pagination, ResponseMeta meta) {

    /**
     * Cursor-based pagination metadata.
     *
     * @param cursor  Opaque base64-encoded cursor for the next page. {@code null} if no next page.
     * @param hasMore {@code true} if there are more pages after this one.
     * @param limit   Number of items per page requested.
     * @param total   Total item count — only populated when total is &lt; 10,000 to avoid
     *                expensive COUNT queries on large datasets. Otherwise {@code null}.
     */
    public record Pagination(String cursor, boolean hasMore, int limit, Long total) {

        /** Convenience constructor without total count. */
        public Pagination(String cursor, boolean hasMore, int limit) {
            this(cursor, hasMore, limit, null);
        }
    }

    /**
     * Convenience factory — creates a paged response with auto-generated metadata.
     *
     * @param data       Page data.
     * @param pagination Pagination details.
     * @param apiVersion API version string.
     * @param <T>        Item type.
     * @return Wrapped paged response.
     */
    public static <T> PagedApiResponse<T> of(List<T> data, Pagination pagination, String apiVersion) {
        ResponseMeta meta = new ResponseMeta(
                UUID.randomUUID().toString(),
                Instant.now(),
                apiVersion
        );
        return new PagedApiResponse<>(data, pagination, meta);
    }

    /**
     * Convenience factory using the default "v1" API version.
     *
     * @param data       Page data.
     * @param pagination Pagination details.
     * @param <T>        Item type.
     * @return Wrapped paged response.
     */
    public static <T> PagedApiResponse<T> of(List<T> data, Pagination pagination) {
        return of(data, pagination, "v1");
    }
}
