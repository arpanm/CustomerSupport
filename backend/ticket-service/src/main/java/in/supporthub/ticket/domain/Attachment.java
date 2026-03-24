package in.supporthub.ticket.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Represents a file attachment uploaded via a MinIO presigned PUT URL.
 *
 * <p>Lifecycle:
 * <ol>
 *   <li>Client calls {@code POST /api/v1/attachments/presign} → attachment is saved with status {@code PENDING}.</li>
 *   <li>Client PUTs the file bytes directly to the presigned MinIO URL.</li>
 *   <li>When the ticket is created, the service links all attachment IDs and sets status to {@code LINKED}.</li>
 * </ol>
 *
 * <p>Row-Level Security is enforced at the database level via the {@code tenant_id} column.
 */
@Entity
@Table(name = "attachments")
@Getter
@Setter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class Attachment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Tenant that owns this attachment — populated from {@code TenantContextHolder}. */
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    /**
     * Ticket this attachment belongs to.
     * Nullable until the ticket creation request is fully processed.
     */
    @Column(name = "ticket_id")
    private UUID ticketId;

    /** Original file name provided by the client. */
    @Column(name = "file_name", nullable = false, length = 500)
    private String fileName;

    /** MIME type of the file (e.g. {@code image/png}, {@code application/pdf}). */
    @Column(name = "content_type", nullable = false, length = 200)
    private String contentType;

    /** Object key inside the MinIO bucket (tenant-namespaced). */
    @Column(name = "minio_object_key", nullable = false, length = 1000)
    private String minioObjectKey;

    /** File size in bytes as reported at presign time. */
    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AttachmentStatus status;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
