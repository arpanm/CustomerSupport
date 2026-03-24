package in.supporthub.ticket.service;

import in.supporthub.shared.security.TenantContextHolder;
import in.supporthub.ticket.domain.Attachment;
import in.supporthub.ticket.domain.AttachmentStatus;
import in.supporthub.ticket.dto.PresignAttachmentRequest;
import in.supporthub.ticket.dto.PresignAttachmentResponse;
import in.supporthub.ticket.repository.AttachmentRepository;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Business logic for file attachment lifecycle management.
 *
 * <p>Generates MinIO presigned PUT URLs so that clients upload file bytes directly
 * to object storage without routing data through the application tier.
 *
 * <p>All operations are tenant-scoped via {@link TenantContextHolder}.
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class AttachmentService {

    private final AttachmentRepository attachmentRepository;
    private final MinioClient minioClient;

    @Value("${supporthub.minio.bucket}")
    private String bucket;

    @Value("${supporthub.minio.presign-expiry-minutes:15}")
    private int presignExpiryMinutes;

    /**
     * Generates a MinIO presigned PUT URL for a new file attachment and persists
     * the attachment metadata with status {@code PENDING}.
     *
     * <p>The client must subsequently PUT the file bytes to the returned URL,
     * then include the returned {@code attachmentId} in the
     * {@link in.supporthub.ticket.dto.CreateTicketRequest}.
     *
     * @param tenantId the tenant UUID extracted from the request context
     * @param request  the file name and content type
     * @return presigned upload URL and new attachment ID
     */
    public PresignAttachmentResponse presignUpload(UUID tenantId, PresignAttachmentRequest request) {
        UUID attachmentId = UUID.randomUUID();
        // Object key is tenant-namespaced to enforce isolation inside the shared bucket
        String objectKey = tenantId + "/" + attachmentId + "/" + sanitize(request.fileName());

        String uploadUrl = generatePresignedUrl(objectKey, request.contentType());

        Attachment attachment = Attachment.builder()
                .id(attachmentId)
                .tenantId(tenantId)
                .fileName(request.fileName())
                .contentType(request.contentType())
                .minioObjectKey(objectKey)
                .status(AttachmentStatus.PENDING)
                .build();

        attachmentRepository.save(attachment);

        log.info("attachment.presigned: attachmentId={} tenantId={} objectKey={}",
                attachmentId, tenantId, objectKey);

        return new PresignAttachmentResponse(uploadUrl, attachmentId);
    }

    /**
     * Links a list of attachment IDs to a ticket and transitions their status to {@code LINKED}.
     *
     * <p>Called by {@link TicketService} immediately after a ticket is persisted.
     *
     * @param attachmentIds list of attachment UUIDs to link
     * @param ticketId      the newly created ticket UUID
     * @param tenantId      tenant UUID for isolation
     */
    public void linkAttachmentsToTicket(List<UUID> attachmentIds, UUID ticketId, UUID tenantId) {
        if (attachmentIds.isEmpty()) return;
        attachmentRepository.linkToTicket(attachmentIds, ticketId, AttachmentStatus.LINKED, tenantId);
        log.info("attachment.linked: count={} ticketId={} tenantId={}", attachmentIds.size(), ticketId, tenantId);
    }

    // ---------------------------------------------------------------------------
    // Private helpers
    // ---------------------------------------------------------------------------

    private String generatePresignedUrl(String objectKey, String contentType) {
        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.PUT)
                            .bucket(bucket)
                            .object(objectKey)
                            .expiry(presignExpiryMinutes, TimeUnit.MINUTES)
                            .extraQueryParams(java.util.Map.of("Content-Type", contentType))
                            .build()
            );
        } catch (Exception ex) {
            // Wrap checked exceptions from MinIO SDK into a runtime exception
            throw new IllegalStateException("Failed to generate presigned URL for object: " + objectKey, ex);
        }
    }

    /**
     * Removes path separators from file names to prevent directory traversal
     * inside the MinIO object key.
     */
    private static String sanitize(String fileName) {
        return fileName.replaceAll("[/\\\\]", "_");
    }
}
