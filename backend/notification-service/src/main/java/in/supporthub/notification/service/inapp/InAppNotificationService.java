package in.supporthub.notification.service.inapp;

import in.supporthub.notification.domain.Notification;
import in.supporthub.notification.domain.Notification.Channel;
import in.supporthub.notification.domain.Notification.RecipientType;
import in.supporthub.notification.domain.Notification.Status;
import in.supporthub.notification.exception.NotificationNotFoundException;
import in.supporthub.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;

/**
 * Service for in-app notification management.
 *
 * <p>Persists notifications to MongoDB and provides read/mark-as-read operations
 * for the REST controller. In-app notifications are created with status SENT
 * and transition to DELIVERED when the user acknowledges them.
 *
 * <p>Content fields must NEVER contain PII.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class InAppNotificationService {

    private final NotificationRepository notificationRepository;

    /**
     * Creates and persists an in-app notification with status SENT.
     *
     * @param tenantId      Tenant scope.
     * @param recipientId   UUID of the recipient user.
     * @param recipientType Whether the recipient is a CUSTOMER or AGENT.
     * @param subject       Short title — must not contain PII.
     * @param content       Body text — must not contain PII.
     * @param referenceId   External reference UUID (e.g., ticketId).
     * @param referenceType External reference type (e.g., "TICKET").
     * @param templateParams Optional substitution params — must not contain PII.
     * @return The persisted {@link Notification} document.
     */
    public Notification saveNotification(
            String tenantId,
            String recipientId,
            RecipientType recipientType,
            String subject,
            String content,
            String referenceId,
            String referenceType,
            Map<String, String> templateParams) {

        Notification notification = Notification.builder()
                .tenantId(tenantId)
                .recipientId(recipientId)
                .recipientType(recipientType)
                .channel(Channel.IN_APP)
                .status(Status.SENT)
                .subject(subject)
                .content(content)
                .referenceId(referenceId)
                .referenceType(referenceType)
                .templateParams(templateParams)
                .attempts(1)
                .lastAttemptAt(Instant.now())
                .sentAt(Instant.now())
                .createdAt(Instant.now())
                .build();

        Notification saved = notificationRepository.save(notification);
        log.info("In-app notification saved tenantId={} recipientId={} referenceId={}",
                tenantId, recipientId, referenceId);
        return saved;
    }

    /**
     * Returns the count of unread in-app notifications for a recipient.
     *
     * <p>Unread = status is SENT (the user has not acknowledged them yet).
     *
     * @param tenantId    Tenant scope.
     * @param recipientId UUID of the recipient.
     * @return Count of unread notifications.
     */
    public long getUnreadCount(String tenantId, String recipientId) {
        return notificationRepository.countByTenantIdAndRecipientIdAndStatus(
                tenantId, recipientId, Status.SENT);
    }

    /**
     * Returns a paginated list of all notifications for a recipient, newest first.
     *
     * @param tenantId    Tenant scope.
     * @param recipientId UUID of the recipient.
     * @param pageable    Pagination params.
     * @return Paginated notifications.
     */
    public Page<Notification> getNotifications(String tenantId, String recipientId, Pageable pageable) {
        return notificationRepository.findByTenantIdAndRecipientIdOrderByCreatedAtDesc(
                tenantId, recipientId, pageable);
    }

    /**
     * Marks a specific in-app notification as read (status DELIVERED).
     *
     * <p>Verifies that the notification belongs to the requesting recipient within the tenant
     * before updating, to prevent cross-user data access.
     *
     * @param tenantId       Tenant scope.
     * @param notificationId MongoDB document ID.
     * @param recipientId    UUID of the requesting user — must own the notification.
     * @throws NotificationNotFoundException if the notification does not exist or does not belong
     *                                        to the specified recipient within the tenant.
     */
    public void markAsRead(String tenantId, String notificationId, String recipientId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new NotificationNotFoundException(notificationId));

        if (!tenantId.equals(notification.getTenantId())
                || !recipientId.equals(notification.getRecipientId())) {
            // Log with IDs only — no PII
            log.warn("Unauthorized markAsRead attempt notificationId={} tenantId={} requestedBy={}",
                    notificationId, tenantId, recipientId);
            throw new NotificationNotFoundException(notificationId);
        }

        notification.setStatus(Status.DELIVERED);
        notificationRepository.save(notification);

        log.info("Notification marked as read notificationId={} tenantId={}", notificationId, tenantId);
    }
}
