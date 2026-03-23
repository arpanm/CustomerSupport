package in.supporthub.faq.dto;

import java.util.List;

/**
 * Represents the {@code entry} payload within a Strapi webhook event.
 *
 * <p>Field names match the Strapi v5 content-type API response format.
 * {@code publishedAt} is non-null when the entry is published in Strapi.
 */
public record StrapiEntry(
        String id,
        String question,
        String answer,
        String category,
        List<String> tags,
        String publishedAt
) {
}
