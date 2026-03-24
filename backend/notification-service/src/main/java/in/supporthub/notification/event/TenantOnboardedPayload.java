package in.supporthub.notification.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Deserialized payload for the {@code tenant.onboarded} Kafka event.
 *
 * <p>Unknown fields are silently ignored so that producer-side additions
 * do not break this consumer.
 *
 * @param tenantId   UUID string of the newly onboarded tenant.
 * @param slug       URL-safe slug identifying the tenant (e.g., "acme-corp").
 * @param name       Human-readable organisation name.
 * @param planType   Subscription plan identifier (e.g., "STARTER", "PRO", "ENTERPRISE").
 * @param adminEmail Email address of the primary admin — may be {@code null}; handle gracefully.
 * @param occurredAt ISO-8601 timestamp when onboarding completed.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TenantOnboardedPayload(
        String tenantId,
        String slug,
        String name,
        String planType,
        String adminEmail,
        String occurredAt
) {}
