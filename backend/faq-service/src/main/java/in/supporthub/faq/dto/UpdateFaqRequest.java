package in.supporthub.faq.dto;

import java.util.List;
import java.util.UUID;

/**
 * Request DTO for updating an existing FAQ entry.
 *
 * <p>All fields are optional (nullable). Only non-null fields are applied to
 * the stored entity. If {@code question} or {@code answer} changes, the
 * embedding is regenerated.
 */
public record UpdateFaqRequest(
        String question,
        String answer,
        UUID categoryId,
        List<String> tags
) {
}
