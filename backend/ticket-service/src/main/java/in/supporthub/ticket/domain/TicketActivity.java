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
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Represents a single activity entry in a ticket's conversation history and audit trail.
 *
 * <p>Activities are append-only — once created they are never updated.
 * This preserves a complete, immutable audit log.
 */
@Entity
@Table(name = "ticket_activities")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(onlyExplicitlyIncluded = true)
public class TicketActivity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @ToString.Include
    private UUID id;

    @Column(name = "ticket_id", nullable = false)
    @ToString.Include
    private UUID ticketId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "actor_id", nullable = false)
    private UUID actorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_type", nullable = false, length = 20)
    private ActorType actorType;

    @Enumerated(EnumType.STRING)
    @Column(name = "activity_type", nullable = false, length = 30)
    @ToString.Include
    private ActivityType activityType;

    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    @Column(name = "is_internal", nullable = false)
    @Builder.Default
    private boolean internal = false;

    /**
     * JSON array of attachment objects: [{ file_name, s3_key, mime_type, size_bytes }].
     */
    @Column(name = "attachments", columnDefinition = "jsonb")
    private String attachments;

    /**
     * Activity-type-specific metadata (e.g., old/new status for STATUS_CHANGE events).
     */
    @Column(name = "metadata", columnDefinition = "jsonb")
    private String metadata;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
