package in.supporthub.notification.dto;

import in.supporthub.notification.domain.Notification.Channel;
import in.supporthub.notification.domain.Notification.Status;

import java.time.Instant;

/**
 * Immutable response DTO for a single notification.
 *
 * <p>Returned by the REST controller; does not expose internal fields (attempts, failureReason).
 * Content field must not contain PII.
 */
public record NotificationResponse(
        String id,
        Channel channel,
        String subject,
        String content,
        String referenceId,
        String referenceType,
        Status status,
        Instant createdAt
) {}
