package in.supporthub.shared.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Generic success response envelope for single-object API responses.
 *
 * <p>All non-paginated success responses MUST use this envelope.
 *
 * <p>Example JSON:
 * <pre>{@code
 * {
 *   "data": { ... },
 *   "meta": {
 *     "requestId": "uuid",
 *     "timestamp": "2026-03-23T10:00:00Z",
 *     "apiVersion": "v1"
 *   }
 * }
 * }</pre>
 *
 * @param <T>  Type of the payload object.
 * @param data The response payload.
 * @param meta Standard response metadata.
 */
public record ApiResponse<T>(T data, ResponseMeta meta) {

    /**
     * Convenience factory — creates an ApiResponse with auto-generated requestId and current timestamp.
     *
     * @param data       Response payload.
     * @param apiVersion API version string (e.g., "v1").
     * @param <T>        Payload type.
     * @return Wrapped response.
     */
    public static <T> ApiResponse<T> of(T data, String apiVersion) {
        ResponseMeta meta = new ResponseMeta(
                UUID.randomUUID().toString(),
                Instant.now(),
                apiVersion
        );
        return new ApiResponse<>(data, meta);
    }

    /**
     * Convenience factory using the default "v1" API version.
     *
     * @param data Payload.
     * @param <T>  Payload type.
     * @return Wrapped response.
     */
    public static <T> ApiResponse<T> of(T data) {
        return of(data, "v1");
    }
}
