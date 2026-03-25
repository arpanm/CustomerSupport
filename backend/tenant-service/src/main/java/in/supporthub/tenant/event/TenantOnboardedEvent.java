package in.supporthub.tenant.event;

import in.supporthub.tenant.domain.PlanType;

import java.time.Instant;
import java.util.UUID;

/**
 * Kafka event published when a new tenant has been successfully onboarded.
 *
 * <p>Topic: {@code tenant.onboarded}
 *
 * <p>Consumers: notification-service (welcome notifications), reporting-service (tenant metrics).
 *
 * @param tenantId   UUID of the newly created tenant.
 * @param slug       URL-friendly slug of the tenant.
 * @param name       Human-readable display name of the tenant.
 * @param planType   Subscription plan tier assigned at onboarding.
 * @param occurredAt Timestamp when the tenant was created.
 */
public record TenantOnboardedEvent(
        UUID tenantId,
        String slug,
        String name,
        PlanType planType,
        Instant occurredAt
) {
}
