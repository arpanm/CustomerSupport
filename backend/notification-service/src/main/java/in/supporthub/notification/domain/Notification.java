package in.supporthub.notification.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Map;

/**
 * MongoDB document representing a notification sent to a user.
 *
 * <p>Supports multi-channel delivery: SMS, EMAIL, IN_APP, WHATSAPP.
 * Content fields must NEVER contain PII (phone, email, name).
 * Template params may hold ticket-scoped data only (ticket numbers, statuses).
 */
@Document(collection = "notifications")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Notification {

    @Id
    private String id;

    /** Tenant identifier — required on every document for multi-tenancy. */
    @Indexed
    private String tenantId;

    /** UUID of the recipient user (customer or agent). */
    @Indexed
    private String recipientId;

    /** Whether the recipient is a CUSTOMER or AGENT. */
    private RecipientType recipientType;

    /** Delivery channel for this notification. */
    private Channel channel;

    /** Current delivery status. */
    @Indexed
    private Status status;

    /** Subject line — for EMAIL channel only; safe to display. */
    private String subject;

    /**
     * Notification body content.
     * MUST NOT contain PII (phone numbers, email addresses, names).
     * Only ticket-scoped data such as ticket numbers and status labels.
     */
    private String content;

    /** Template identifier used to render this notification. */
    private String templateId;

    /**
     * Template variable substitutions (e.g., ticketNumber, status).
     * MUST NOT contain PII values.
     */
    private Map<String, String> templateParams;

    /** External reference ID (e.g., ticketId). */
    @Indexed
    private String referenceId;

    /** Type of external reference (e.g., "TICKET"). */
    private String referenceType;

    /** Number of delivery attempts made. */
    @Builder.Default
    private int attempts = 0;

    /** Timestamp of the last delivery attempt. */
    private Instant lastAttemptAt;

    /** Timestamp when the notification was successfully sent. */
    private Instant sentAt;

    /** Reason for delivery failure, if applicable. */
    private String failureReason;

    /** Timestamp when this document was created. */
    @Indexed
    private Instant createdAt;

    // -------------------------------------------------------------------------
    // Enums
    // -------------------------------------------------------------------------

    public enum RecipientType {
        CUSTOMER,
        AGENT
    }

    public enum Channel {
        SMS,
        EMAIL,
        IN_APP,
        WHATSAPP
    }

    public enum Status {
        PENDING,
        SENT,
        FAILED,
        DELIVERED
    }
}
