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
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Core ticket entity — the central aggregate of the SupportHub platform.
 *
 * <p>Represents a single customer support request throughout its full lifecycle.
 * All queries MUST include {@code tenantId} to enforce multi-tenant isolation.
 *
 * <p>Row-Level Security is enforced at the database level via PostgreSQL RLS policies.
 * The application-level tenant filter sets {@code app.current_tenant} as the session variable.
 */
@Entity
@Table(name = "tickets")
@DynamicUpdate
@Getter
@Setter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@ToString(onlyExplicitlyIncluded = true)
public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @ToString.Include
    private UUID id;

    @Column(name = "ticket_number", unique = true, nullable = false, length = 30)
    @ToString.Include
    private String ticketNumber;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "order_id")
    private UUID orderId;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT", nullable = false)
    private String description;

    @Column(name = "category_id")
    private UUID categoryId;

    @Column(name = "sub_category_id")
    private UUID subCategoryId;

    @Enumerated(EnumType.STRING)
    @Column(name = "ticket_type", length = 20)
    private TicketType ticketType;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = 20)
    private Priority priority;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    @ToString.Include
    private TicketStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", length = 20)
    private Channel channel;

    @Column(name = "assigned_agent_id")
    private UUID assignedAgentId;

    @Column(name = "assigned_team_id")
    private UUID assignedTeamId;

    /**
     * Tenant-specific extra fields stored as a JSON object string.
     * Persisted as JSONB in PostgreSQL for efficient querying.
     */
    @Column(name = "custom_fields", columnDefinition = "jsonb")
    private String customFields;

    /**
     * Free-form text tags for categorization and filtering.
     * Persisted as a PostgreSQL TEXT array.
     */
    @Column(name = "tags", columnDefinition = "text[]")
    private String[] tags;

    @Column(name = "sla_first_response_due_at")
    private Instant slaFirstResponseDueAt;

    @Column(name = "sla_resolution_due_at")
    private Instant slaResolutionDueAt;

    @Column(name = "sla_first_response_breached", nullable = false)
    @Builder.Default
    private boolean slaFirstResponseBreached = false;

    @Column(name = "sla_resolution_breached", nullable = false)
    @Builder.Default
    private boolean slaResolutionBreached = false;

    @Column(name = "sentiment_score")
    private Float sentimentScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "sentiment_label", length = 20)
    private SentimentLabel sentimentLabel;

    @Column(name = "sentiment_updated_at")
    private Instant sentimentUpdatedAt;

    @Column(name = "first_responded_at")
    private Instant firstRespondedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
