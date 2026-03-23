package in.supporthub.faq.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;
import java.util.UUID;

/**
 * Request DTO for creating a new FAQ entry.
 *
 * <p>New FAQs are created in an unpublished state ({@code isPublished=false})
 * and must be explicitly published via the publish endpoint before appearing
 * in public search results.
 */
public record CreateFaqRequest(
        @NotBlank(message = "question must not be blank")
        String question,

        @NotBlank(message = "answer must not be blank")
        String answer,

        UUID categoryId,

        List<String> tags
) {
}
