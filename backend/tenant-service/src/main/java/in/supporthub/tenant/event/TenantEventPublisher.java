package in.supporthub.tenant.event;

import in.supporthub.tenant.exception.TenantOnboardingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes tenant-related Kafka events to the appropriate topics.
 *
 * <p>Business code must never use raw {@link KafkaTemplate} directly — always
 * go through this publisher.
 *
 * <p>Topics:
 * <ul>
 *   <li>{@code tenant.onboarded}</li>
 * </ul>
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class TenantEventPublisher {

    static final String TOPIC_TENANT_ONBOARDED = "tenant.onboarded";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * Publishes a {@code tenant.onboarded} event after successful tenant creation.
     *
     * <p>Only tenantId is logged — never PII (name, email, etc.).
     *
     * @param event the event record populated by {@link in.supporthub.tenant.service.TenantService}
     * @throws TenantOnboardingException if the Kafka send fails
     */
    public void publishTenantOnboarded(TenantOnboardedEvent event) {
        try {
            kafkaTemplate.send(TOPIC_TENANT_ONBOARDED, event.tenantId().toString(), event);
            log.info("Published tenant.onboarded: tenantId={}", event.tenantId());
        } catch (Exception ex) {
            log.error("Failed to publish tenant.onboarded: tenantId={}, error={}",
                    event.tenantId(), ex.getMessage(), ex);
            throw new TenantOnboardingException(event.tenantId().toString(), ex);
        }
    }
}
