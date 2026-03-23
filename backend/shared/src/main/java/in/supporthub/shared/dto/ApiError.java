package in.supporthub.shared.dto;

import java.util.Map;

/**
 * Standard error response envelope returned for all HTTP 4xx and 5xx responses.
 *
 * <p>Example JSON:
 * <pre>{@code
 * {
 *   "error": {
 *     "code": "TICKET_NOT_FOUND",
 *     "message": "Ticket not found: FC-2024-001234",
 *     "details": null,
 *     "traceId": "uuid"
 *   },
 *   "meta": {
 *     "requestId": "uuid",
 *     "timestamp": "2026-03-23T10:00:00Z",
 *     "apiVersion": "v1"
 *   }
 * }
 * }</pre>
 *
 * @param error Structured error details.
 * @param meta  Standard response metadata.
 */
public record ApiError(ErrorDetail error, ResponseMeta meta) {

    /**
     * Structured error detail included in every error response.
     *
     * @param code     Machine-readable error code (e.g., "TICKET_NOT_FOUND", "VALIDATION_FAILED").
     *                 Clients should switch on this code — NOT on the message string.
     * @param message  Human-readable error description — safe to show to end users.
     * @param details  Optional map of additional context (e.g., per-field validation errors).
     *                 {@code null} when not applicable.
     * @param traceId  Server-generated trace ID for correlating this error with server-side logs.
     *                 Include this in bug reports and support requests.
     */
    public record ErrorDetail(
            String code,
            String message,
            Map<String, Object> details,
            String traceId) {}
}
