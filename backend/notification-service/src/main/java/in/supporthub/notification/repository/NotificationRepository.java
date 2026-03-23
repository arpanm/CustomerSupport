package in.supporthub.notification.repository;

import in.supporthub.notification.domain.Notification;
import in.supporthub.notification.domain.Notification.Status;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * MongoDB repository for {@link Notification} documents.
 *
 * <p>All queries are scoped to {@code tenantId} to enforce multi-tenant isolation.
 */
@Repository
public interface NotificationRepository extends MongoRepository<Notification, String> {

    /**
     * Returns a paginated list of notifications for a recipient filtered by status,
     * ordered by creation time descending (newest first).
     *
     * @param tenantId    Tenant scope.
     * @param recipientId UUID of the recipient.
     * @param status      Delivery status filter.
     * @param pageable    Pagination params.
     * @return Paginated notification results.
     */
    Page<Notification> findByTenantIdAndRecipientIdAndStatusOrderByCreatedAtDesc(
            String tenantId, String recipientId, Status status, Pageable pageable);

    /**
     * Returns all notifications for a given reference (e.g., all notifications for a ticket).
     *
     * @param tenantId      Tenant scope.
     * @param referenceId   External entity ID (e.g., ticketId).
     * @param referenceType Type of the external entity (e.g., "TICKET").
     * @return All matching notifications.
     */
    List<Notification> findByTenantIdAndReferenceIdAndReferenceType(
            String tenantId, String referenceId, String referenceType);

    /**
     * Returns unread in-app notifications for a recipient (channel=IN_APP, status=SENT).
     *
     * @param tenantId    Tenant scope.
     * @param recipientId UUID of the recipient.
     * @return Unread in-app notifications.
     */
    @Query("{ 'tenantId': ?0, 'recipientId': ?1, 'channel': 'IN_APP', 'status': 'SENT' }")
    List<Notification> findUnreadByRecipientId(String tenantId, String recipientId);

    /**
     * Returns the count of unread in-app notifications (status=SENT) for a recipient.
     *
     * @param tenantId    Tenant scope.
     * @param recipientId UUID of the recipient.
     * @param status      Status to count (expected: "SENT" for unread).
     * @return Count of matching notifications.
     */
    long countByTenantIdAndRecipientIdAndStatus(String tenantId, String recipientId, Status status);

    /**
     * Returns a paginated list of all notifications for a recipient across all statuses,
     * ordered by creation time descending.
     *
     * @param tenantId    Tenant scope.
     * @param recipientId UUID of the recipient.
     * @param pageable    Pagination params.
     * @return Paginated notification results.
     */
    Page<Notification> findByTenantIdAndRecipientIdOrderByCreatedAtDesc(
            String tenantId, String recipientId, Pageable pageable);
}
