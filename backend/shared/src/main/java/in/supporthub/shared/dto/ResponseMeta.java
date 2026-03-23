package in.supporthub.shared.dto;

import java.time.Instant;

/**
 * Standard metadata included in every API response envelope.
 *
 * @param requestId  Unique identifier for the originating request — use for log correlation.
 * @param timestamp  Server-side timestamp of when the response was generated (UTC).
 * @param apiVersion API version string (e.g., "v1").
 */
public record ResponseMeta(
        String requestId,
        Instant timestamp,
        String apiVersion) {}
