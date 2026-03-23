package in.supporthub.notification.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.supporthub.notification.domain.Notification;
import in.supporthub.notification.domain.Notification.Channel;
import in.supporthub.notification.domain.Notification.RecipientType;
import in.supporthub.notification.domain.Notification.Status;
import in.supporthub.notification.repository.NotificationRepository;
import in.supporthub.notification.service.email.SendGridEmailService;
import in.supporthub.notification.service.inapp.InAppNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * Kafka consumer for the {@code tenant.onboarded} event.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Send a branded welcome email to the new tenant's admin user via SendGrid.</li>
 *   <li>Persist a welcome notification document in MongoDB for audit and in-app display.</li>
 *   <li>Ensure idempotent processing via a Redis key with a 7-day TTL.</li>
 * </ul>
 *
 * <p>Uses the {@code stringKafkaListenerContainerFactory} because the tenant-service
 * publishes raw JSON without Spring-Kafka type headers. The consumer performs manual
 * deserialization with Jackson.
 *
 * <p>Email failures and MongoDB save failures are non-fatal: they are logged at
 * WARN level and do not cause Kafka offset rollback. The idempotency key is set
 * before attempting delivery so that a partial success is not re-processed on restart.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TenantOnboardedEventConsumer {

    private static final String IDEMPOTENCY_KEY_PREFIX = "notif:tenant:onboarded:";
    private static final Duration IDEMPOTENCY_TTL = Duration.ofDays(7);

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final StringRedisTemplate redisTemplate;
    private final SendGridEmailService emailService;
    private final InAppNotificationService inAppNotificationService;
    private final NotificationRepository notificationRepository;

    /**
     * Processes a {@code tenant.onboarded} event.
     *
     * <p>Idempotency: the Redis key {@code notif:tenant:onboarded:{tenantId}} guards
     * against duplicate deliveries on consumer group rebalances or broker retries.
     * The key is written before delivery attempts so that a JVM crash mid-send does not
     * cause duplicate emails on restart (at-most-once email, at-least-once Kafka commit).
     *
     * @param record Raw Kafka record containing the JSON-serialized {@link TenantOnboardedPayload}.
     */
    @KafkaListener(
            topics = "tenant.onboarded",
            groupId = "notification-service-tenant",
            containerFactory = "stringKafkaListenerContainerFactory"
    )
    public void consume(ConsumerRecord<String, String> record) {
        if (record.value() == null || record.value().isBlank()) {
            log.error("TenantOnboardedConsumer: received null/empty payload at offset={}", record.offset());
            return;
        }

        TenantOnboardedPayload event;
        try {
            event = OBJECT_MAPPER.readValue(record.value(), TenantOnboardedPayload.class);
        } catch (Exception e) {
            log.error("TenantOnboardedConsumer: failed to parse event at offset={}, error={}",
                    record.offset(), e.getMessage());
            // Parsing failure is non-retryable — skip to avoid infinite loop
            return;
        }

        if (event.tenantId() == null || event.tenantId().isBlank()) {
            log.error("TenantOnboardedConsumer: event missing tenantId at offset={}", record.offset());
            return;
        }

        // Idempotency guard — setIfAbsent returns false for duplicate events
        String idempotencyKey = IDEMPOTENCY_KEY_PREFIX + event.tenantId();
        Boolean isNew = redisTemplate.opsForValue().setIfAbsent(idempotencyKey, "1", IDEMPOTENCY_TTL);
        if (Boolean.FALSE.equals(isNew)) {
            log.info("TenantOnboardedConsumer: duplicate event for tenantId={}, skipping", event.tenantId());
            return;
        }

        log.info("TenantOnboardedConsumer: processing welcome notifications for tenantId={}", event.tenantId());

        sendWelcomeEmail(event);
        saveWelcomeNotification(event);
        saveWelcomeInAppNotification(event);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Sends a branded HTML welcome email to the tenant admin via SendGrid.
     *
     * <p>Email address is never logged — only the {@code tenantId} appears in logs.
     * A missing or blank {@code adminEmail} falls back to a derived address based on slug.
     *
     * @param event Parsed onboarding event.
     */
    private void sendWelcomeEmail(TenantOnboardedPayload event) {
        try {
            String adminEmail = (event.adminEmail() != null && !event.adminEmail().isBlank())
                    ? event.adminEmail()
                    : "admin@" + event.slug() + ".supporthub.in";

            String subject = "Welcome to SupportHub — Your account is ready";
            String htmlBody = buildWelcomeEmailHtml(event.name(), event.slug(), event.planType());

            boolean sent = emailService.sendEmail(adminEmail, subject, htmlBody, event.tenantId());
            if (sent) {
                log.info("TenantOnboardedConsumer: welcome email sent for tenantId={}", event.tenantId());
            } else {
                log.warn("TenantOnboardedConsumer: welcome email not sent (SendGrid returned false) for tenantId={}",
                        event.tenantId());
            }
        } catch (Exception e) {
            // Non-fatal: email failure must not prevent notification record creation
            log.warn("TenantOnboardedConsumer: welcome email failed for tenantId={}, error={}",
                    event.tenantId(), e.getMessage());
        }
    }

    /**
     * Builds the HTML body for the welcome email.
     *
     * <p>Substitutes organisation name, plan type, and slug into the template.
     * The {@code adminEmail} is intentionally absent from the rendered body.
     *
     * @param name     Organisation / tenant display name.
     * @param slug     URL-safe tenant slug.
     * @param planType Subscription plan label.
     * @return Rendered HTML string.
     */
    private String buildWelcomeEmailHtml(String name, String slug, String planType) {
        return """
                <!DOCTYPE html>
                <html>
                <head><meta charset="UTF-8"><style>
                  body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif; background: #f5f5f5; margin: 0; padding: 20px; }
                  .container { max-width: 600px; margin: 0 auto; background: white; border-radius: 8px; padding: 40px; }
                  .logo { font-size: 24px; font-weight: 700; color: #007AFF; margin-bottom: 32px; }
                  h1 { font-size: 28px; color: #1a1a1a; margin-bottom: 16px; }
                  p { color: #555; line-height: 1.6; }
                  .info-box { background: #f0f7ff; border-left: 4px solid #007AFF; padding: 16px; border-radius: 4px; margin: 24px 0; }
                  .btn { display: inline-block; background: #007AFF; color: white; padding: 12px 24px; border-radius: 6px; text-decoration: none; font-weight: 600; margin-top: 24px; }
                  .footer { margin-top: 40px; padding-top: 20px; border-top: 1px solid #eee; font-size: 12px; color: #999; }
                </style></head>
                <body>
                  <div class="container">
                    <div class="logo">SupportHub</div>
                    <h1>Welcome, %s!</h1>
                    <p>Your SupportHub account has been successfully created and is ready to use.</p>
                    <div class="info-box">
                      <strong>Your account details:</strong><br>
                      Organisation: <strong>%s</strong><br>
                      Plan: <strong>%s</strong><br>
                      Tenant Slug: <strong>%s</strong>
                    </div>
                    <p>Here's what you can do next:</p>
                    <ul>
                      <li>Configure your ticket categories and SLA policies</li>
                      <li>Invite your support agents</li>
                      <li>Set up FAQ articles for self-service</li>
                      <li>Integrate with your order management system</li>
                    </ul>
                    <a href="https://app.supporthub.in/admin" class="btn">Go to Admin Dashboard</a>
                    <div class="footer">
                      <p>SupportHub — AI-Native Customer Support Platform<br>
                      If you did not create this account, please contact support@supporthub.in</p>
                    </div>
                  </div>
                </body>
                </html>
                """.formatted(name, name, planType, slug);
    }

    /**
     * Persists an EMAIL-channel welcome notification in MongoDB.
     *
     * <p>Stored with {@code status=SENT} to reflect that email delivery was attempted.
     * The {@code recipientId} is set to the {@code tenantId} because at onboarding time
     * the individual admin user UUID is not yet propagated in this event.
     *
     * @param event Parsed onboarding event.
     */
    private void saveWelcomeNotification(TenantOnboardedPayload event) {
        try {
            Notification notification = Notification.builder()
                    .tenantId(event.tenantId())
                    .recipientId(event.tenantId())
                    .recipientType(RecipientType.AGENT)
                    .channel(Channel.EMAIL)
                    .status(Status.SENT)
                    .subject("Welcome to SupportHub")
                    .content("Your SupportHub account '" + event.name() + "' is ready. Plan: " + event.planType())
                    .referenceId(event.tenantId())
                    .referenceType("TENANT")
                    .attempts(1)
                    .lastAttemptAt(Instant.now())
                    .sentAt(Instant.now())
                    .createdAt(Instant.now())
                    .build();

            notificationRepository.save(notification);
            log.debug("TenantOnboardedConsumer: welcome EMAIL notification saved for tenantId={}", event.tenantId());
        } catch (Exception e) {
            // Non-fatal: MongoDB failure must not affect the idempotency guard or email delivery
            log.warn("TenantOnboardedConsumer: failed to save welcome EMAIL notification for tenantId={}, error={}",
                    event.tenantId(), e.getMessage());
        }
    }

    /**
     * Persists an in-app welcome notification in MongoDB via {@link InAppNotificationService}.
     *
     * <p>The in-app notification allows admin-portal users to see the onboarding
     * confirmation in their notification centre on first login.
     *
     * @param event Parsed onboarding event.
     */
    private void saveWelcomeInAppNotification(TenantOnboardedPayload event) {
        try {
            inAppNotificationService.saveNotification(
                    event.tenantId(),
                    event.tenantId(),
                    RecipientType.AGENT,
                    "Account ready — Welcome to SupportHub",
                    "Your organisation '" + event.name() + "' is live on SupportHub. Plan: " + event.planType(),
                    event.tenantId(),
                    "TENANT",
                    Map.of(
                            "tenantSlug", event.slug() != null ? event.slug() : "",
                            "planType", event.planType() != null ? event.planType() : ""
                    )
            );
            log.debug("TenantOnboardedConsumer: welcome IN_APP notification saved for tenantId={}", event.tenantId());
        } catch (Exception e) {
            // Non-fatal
            log.warn("TenantOnboardedConsumer: failed to save welcome IN_APP notification for tenantId={}, error={}",
                    event.tenantId(), e.getMessage());
        }
    }
}
