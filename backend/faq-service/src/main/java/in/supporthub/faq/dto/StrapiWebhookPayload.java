package in.supporthub.faq.dto;

/**
 * Top-level Strapi webhook payload.
 *
 * <p>Strapi v5 sends a JSON body with an {@code event} string and an {@code entry} object.
 * Supported events:
 * <ul>
 *   <li>{@code entry.create} — new entry created (draft)</li>
 *   <li>{@code entry.update} — existing entry updated</li>
 *   <li>{@code entry.delete} — entry deleted</li>
 *   <li>{@code entry.publish} — entry published (becomes visible to customers)</li>
 *   <li>{@code entry.unpublish} — entry unpublished (hidden from customers)</li>
 * </ul>
 */
public record StrapiWebhookPayload(
        String event,
        StrapiEntry entry
) {
}
