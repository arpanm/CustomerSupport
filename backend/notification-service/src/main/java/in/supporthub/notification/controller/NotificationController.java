package in.supporthub.notification.controller;

import in.supporthub.notification.domain.Notification;
import in.supporthub.notification.dto.NotificationResponse;
import in.supporthub.notification.dto.UnreadCountResponse;
import in.supporthub.notification.service.inapp.InAppNotificationService;
import in.supporthub.shared.security.TenantContextHolder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for in-app notification management.
 *
 * <p>All endpoints read the authenticated user's ID from the {@code X-User-Id} header
 * (set by the API gateway after JWT validation). Tenant ID is read from
 * {@link TenantContextHolder} (populated by {@link TenantContextFilter}).
 *
 * <p>Role enforcement: all endpoints are accessible by authenticated users (CUSTOMER or AGENT)
 * and are scoped to the requesting user's own notifications only.
 */
@RestController
@RequestMapping("/api/v1/notifications")
@Slf4j
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "In-app notification endpoints")
public class NotificationController {

    private static final String USER_ID_HEADER = "X-User-Id";

    private final InAppNotificationService inAppNotificationService;

    /**
     * Returns a paginated list of notifications for the authenticated user.
     *
     * <p>Sorted newest-first. Default page size: 20.
     *
     * @param userId   Authenticated user's UUID from the gateway header.
     * @param pageable Pagination parameters (page, size, sort).
     * @return Paginated notification responses.
     */
    @GetMapping("/me")
    @Operation(summary = "Get my notifications", description = "Returns paginated notifications for the authenticated user")
    public ResponseEntity<Page<NotificationResponse>> getMyNotifications(
            @RequestHeader(USER_ID_HEADER) String userId,
            @PageableDefault(size = 20) Pageable pageable) {

        String tenantId = TenantContextHolder.getTenantId();
        log.debug("getMyNotifications tenantId={} userId={}", tenantId, userId);

        Page<Notification> page = inAppNotificationService.getNotifications(tenantId, userId, pageable);
        Page<NotificationResponse> response = page.map(this::toResponse);

        return ResponseEntity.ok(response);
    }

    /**
     * Returns the count of unread notifications for the authenticated user.
     *
     * @param userId Authenticated user's UUID from the gateway header.
     * @return Unread count response.
     */
    @GetMapping("/me/unread-count")
    @Operation(summary = "Get unread notification count")
    public ResponseEntity<UnreadCountResponse> getUnreadCount(
            @RequestHeader(USER_ID_HEADER) String userId) {

        String tenantId = TenantContextHolder.getTenantId();
        long count = inAppNotificationService.getUnreadCount(tenantId, userId);

        log.debug("getUnreadCount tenantId={} userId={} count={}", tenantId, userId, count);
        return ResponseEntity.ok(new UnreadCountResponse(count));
    }

    /**
     * Marks a specific notification as read (transitions status to DELIVERED).
     *
     * <p>Verifies that the notification belongs to the requesting user before updating.
     *
     * @param id     MongoDB notification document ID.
     * @param userId Authenticated user's UUID from the gateway header.
     * @return 204 No Content on success.
     */
    @PutMapping("/{id}/read")
    @Operation(summary = "Mark notification as read")
    public ResponseEntity<Void> markAsRead(
            @PathVariable String id,
            @RequestHeader(USER_ID_HEADER) String userId) {

        String tenantId = TenantContextHolder.getTenantId();
        log.debug("markAsRead notificationId={} tenantId={} userId={}", id, tenantId, userId);

        inAppNotificationService.markAsRead(tenantId, id, userId);

        return ResponseEntity.noContent().build();
    }

    // -------------------------------------------------------------------------
    // Mapping helpers
    // -------------------------------------------------------------------------

    private NotificationResponse toResponse(Notification n) {
        return new NotificationResponse(
                n.getId(),
                n.getChannel(),
                n.getSubject(),
                n.getContent(),
                n.getReferenceId(),
                n.getReferenceType(),
                n.getStatus(),
                n.getCreatedAt()
        );
    }
}
