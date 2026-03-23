package in.supporthub.faq.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Response DTO for a single FAQ entry.
 *
 * <p>The {@code embedding} field is intentionally excluded from the response
 * to avoid leaking large float arrays to API callers.
 */
public record FaqResponse(
        UUID id,
        UUID tenantId,
        UUID categoryId,
        String question,
        String answer,
        List<String> tags,
        boolean isPublished,
        long viewCount,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
