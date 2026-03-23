package in.supporthub.notification.service;

import in.supporthub.notification.domain.Notification;
import in.supporthub.notification.domain.Notification.Channel;
import in.supporthub.notification.domain.Notification.RecipientType;
import in.supporthub.notification.domain.Notification.Status;
import in.supporthub.notification.repository.NotificationRepository;
import in.supporthub.notification.service.inapp.InAppNotificationService;
import in.supporthub.notification.service.sms.Msg91SmsService;
import in.supporthub.notification.service.whatsapp.WhatsAppService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;

/**
 * Core notification orchestration service.
 *
 * <p>Coordinates multi-channel notification delivery (SMS, WhatsApp, in-app) for ticket events.
 * Each channel is independent — failure in one channel does not block others.
 *
 * <p>PII Handling:
 * <ul>
 *   <li>Phone numbers are decrypted via {@link PiiDecryptionService} immediately before use.</li>
 *   <li>Decrypted values are NEVER logged, stored, or passed beyond the sending method.</li>
 *   <li>Notification content stored in MongoDB must not contain PII.</li>
 * </ul>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class NotificationService {

    private final Msg91SmsService smsService;
    private final WhatsAppService whatsAppService;
    private final InAppNotificationService inAppNotificationService;
    private final NotificationRepository notificationRepository;
    private final PiiDecryptionService piiDecryptionService;

    /**
     * Sends a ticket-created notification to the customer.
     *
     * <p>Delivery channels:
     * <ol>
     *   <li>SMS via MSG91 (using decrypted phone from encrypted PII)</li>
     *   <li>WhatsApp via Meta API (optional, graceful degradation)</li>
     *   <li>In-app notification to MongoDB</li>
     * </ol>
     *
     * <p>SMS/WhatsApp failure does NOT block in-app notification saving.
     *
     * @param tenantId           Tenant scope.
     * @param customerId         UUID of the customer.
     * @param ticketNumber       Human-readable ticket number (e.g., "FC-2024-001234").
     * @param categoryId         Category UUID — used as display reference in content.
     */
    public void sendTicketCreatedNotification(
            String tenantId,
            String customerId,
            String ticketNumber,
            String categoryId) {

        log.info("Sending ticket-created notifications tenantId={} customerId={} ticketNumber={}",
                tenantId, customerId, ticketNumber);

        // In-app notification is always attempted first (no PII required)
        String subject = "Ticket Created: " + ticketNumber;
        String content = "Your support ticket " + ticketNumber + " has been created and is being reviewed.";

        Notification inApp = null;
        try {
            inApp = inAppNotificationService.saveNotification(
                    tenantId, customerId, RecipientType.CUSTOMER,
                    subject, content,
                    null,  // referenceId populated from ticketNumber key
                    "TICKET",
                    Map.of("ticketNumber", ticketNumber)
            );
        } catch (Exception e) {
            log.error("Failed to save in-app notification tenantId={} customerId={} ticketNumber={} error={}",
                    tenantId, customerId, ticketNumber, e.getMessage());
        }

        // Persist the channel notification record for SMS
        persistChannelNotification(tenantId, customerId, RecipientType.CUSTOMER,
                Channel.SMS, subject, content, ticketNumber, "TICKET",
                Map.of("ticketNumber", ticketNumber, "event", "TICKET_CREATED"), Status.PENDING);

        // Persist WhatsApp record
        persistChannelNotification(tenantId, customerId, RecipientType.CUSTOMER,
                Channel.WHATSAPP, subject, content, ticketNumber, "TICKET",
                Map.of("ticketNumber", ticketNumber, "event", "TICKET_CREATED"), Status.PENDING);

        log.info("In-app notification saved for ticket creation tenantId={} ticketNumber={}",
                tenantId, ticketNumber);
    }

    /**
     * Sends a ticket-created notification when a decrypted phone number is available.
     *
     * <p>Intended to be called by a downstream worker or re-tried after phone decryption
     * has been resolved from the customer-service.
     *
     * @param tenantId         Tenant scope.
     * @param customerId       UUID of the customer.
     * @param encryptedPhoneB64 Base64-encoded AES-GCM encrypted phone number from customer-service.
     * @param ticketNumber     Human-readable ticket number.
     * @param categoryName     Display name of the category.
     */
    public void sendTicketCreatedWithPhone(
            String tenantId,
            String customerId,
            String encryptedPhoneB64,
            String ticketNumber,
            String categoryName) {

        log.info("Sending ticket-created SMS/WhatsApp tenantId={} ticketNumber={}", tenantId, ticketNumber);

        // Decrypt phone — NEVER log the decrypted value
        String phone;
        try {
            phone = piiDecryptionService.decryptBase64(encryptedPhoneB64);
        } catch (Exception e) {
            log.error("Phone decryption failed for ticket-created SMS tenantId={} ticketNumber={}: {}",
                    tenantId, ticketNumber, e.getMessage());
            return;
        }

        // SMS — failure is non-fatal
        boolean smsSent = false;
        try {
            smsSent = smsService.sendTicketCreatedSms(phone, ticketNumber, categoryName);
        } catch (Exception e) {
            log.error("SMS dispatch error tenantId={} ticketNumber={} error={}", tenantId, ticketNumber, e.getMessage());
        }

        // WhatsApp — optional, always graceful degradation
        try {
            whatsAppService.sendTicketCreatedMessage(phone, ticketNumber, categoryName);
        } catch (Exception e) {
            log.warn("WhatsApp dispatch error tenantId={} ticketNumber={} error={}", tenantId, ticketNumber, e.getMessage());
        }

        log.info("ticket-created channel dispatch complete tenantId={} ticketNumber={} smsSent={}",
                tenantId, ticketNumber, smsSent);
    }

    /**
     * Sends a ticket status change notification.
     *
     * <p>For RESOLVED status changes — sent to the customer.
     * For ESCALATED status changes — sent to the assigned agent.
     *
     * @param tenantId      Tenant scope.
     * @param recipientId   UUID of the notification recipient (customerId or agentId).
     * @param recipientType Whether the recipient is a CUSTOMER or AGENT.
     * @param oldStatus     Previous ticket status.
     * @param newStatus     New ticket status.
     * @param ticketNumber  Human-readable ticket number.
     * @param ticketId      Internal ticket UUID (for referenceId).
     */
    public void sendStatusChangedNotification(
            String tenantId,
            String recipientId,
            RecipientType recipientType,
            String oldStatus,
            String newStatus,
            String ticketNumber,
            String ticketId) {

        log.info("Sending status-changed notification tenantId={} recipientId={} ticketNumber={} {} -> {}",
                tenantId, recipientId, ticketNumber, oldStatus, newStatus);

        String subject = buildStatusChangeSubject(newStatus, ticketNumber);
        String content = buildStatusChangeContent(newStatus, ticketNumber, recipientType);

        // Always save in-app notification
        try {
            inAppNotificationService.saveNotification(
                    tenantId, recipientId, recipientType,
                    subject, content,
                    ticketId, "TICKET",
                    Map.of("ticketNumber", ticketNumber, "oldStatus", oldStatus, "newStatus", newStatus)
            );
        } catch (Exception e) {
            log.error("Failed to save in-app status notification tenantId={} recipientId={} ticketNumber={} error={}",
                    tenantId, recipientId, ticketNumber, e.getMessage());
        }

        // Persist notification record for external channels (SMS delivery requires phone lookup)
        persistChannelNotification(tenantId, recipientId, recipientType,
                Channel.SMS, subject, content, ticketId, "TICKET",
                Map.of("ticketNumber", ticketNumber, "oldStatus", oldStatus, "newStatus", newStatus),
                Status.PENDING);

        log.info("Status-changed in-app notification saved tenantId={} ticketNumber={} newStatus={}",
                tenantId, ticketNumber, newStatus);
    }

    /**
     * Sends a status change notification when a decrypted phone is available.
     *
     * @param tenantId         Tenant scope.
     * @param encryptedPhoneB64 Base64-encoded encrypted phone.
     * @param ticketNumber     Human-readable ticket number.
     * @param newStatus        New status label.
     */
    public void sendStatusChangedWithPhone(
            String tenantId,
            String encryptedPhoneB64,
            String ticketNumber,
            String newStatus) {

        log.info("Sending status-changed SMS/WhatsApp tenantId={} ticketNumber={} newStatus={}",
                tenantId, ticketNumber, newStatus);

        String phone;
        try {
            phone = piiDecryptionService.decryptBase64(encryptedPhoneB64);
        } catch (Exception e) {
            log.error("Phone decryption failed for status-changed SMS tenantId={} ticketNumber={}: {}",
                    tenantId, ticketNumber, e.getMessage());
            return;
        }

        try {
            smsService.sendStatusChangedSms(phone, ticketNumber, newStatus);
        } catch (Exception e) {
            log.error("Status SMS dispatch error tenantId={} ticketNumber={} error={}",
                    tenantId, ticketNumber, e.getMessage());
        }

        try {
            whatsAppService.sendStatusChangedMessage(phone, ticketNumber, newStatus);
        } catch (Exception e) {
            log.warn("Status WhatsApp dispatch error tenantId={} ticketNumber={} error={}",
                    tenantId, ticketNumber, e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private Notification persistChannelNotification(
            String tenantId,
            String recipientId,
            RecipientType recipientType,
            Channel channel,
            String subject,
            String content,
            String referenceId,
            String referenceType,
            Map<String, String> templateParams,
            Status status) {

        Notification notification = Notification.builder()
                .tenantId(tenantId)
                .recipientId(recipientId)
                .recipientType(recipientType)
                .channel(channel)
                .status(status)
                .subject(subject)
                .content(content)
                .referenceId(referenceId)
                .referenceType(referenceType)
                .templateParams(templateParams)
                .attempts(0)
                .createdAt(Instant.now())
                .build();

        return notificationRepository.save(notification);
    }

    private String buildStatusChangeSubject(String newStatus, String ticketNumber) {
        return switch (newStatus) {
            case "RESOLVED" -> "Ticket Resolved: " + ticketNumber;
            case "ESCALATED" -> "Ticket Escalated: " + ticketNumber;
            case "CLOSED" -> "Ticket Closed: " + ticketNumber;
            default -> "Ticket Update: " + ticketNumber;
        };
    }

    private String buildStatusChangeContent(String newStatus, String ticketNumber, RecipientType recipientType) {
        return switch (newStatus) {
            case "RESOLVED" -> "Your ticket " + ticketNumber + " has been resolved. "
                    + "Please let us know if you need further assistance.";
            case "ESCALATED" -> recipientType == RecipientType.AGENT
                    ? "Ticket " + ticketNumber + " has been escalated and assigned to you. Please review urgently."
                    : "Your ticket " + ticketNumber + " has been escalated for priority handling.";
            case "CLOSED" -> "Your ticket " + ticketNumber + " has been closed.";
            default -> "Your ticket " + ticketNumber + " status has been updated to " + newStatus + ".";
        };
    }
}
