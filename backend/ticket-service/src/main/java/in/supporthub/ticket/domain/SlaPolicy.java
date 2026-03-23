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
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * SLA policy rule — defines first-response and resolution time thresholds.
 *
 * <p>Matching precedence (most specific wins):
 * <ol>
 *   <li>category + priority</li>
 *   <li>category only (priority is null)</li>
 *   <li>priority only (categoryId is null)</li>
 *   <li>default (both null)</li>
 * </ol>
 */
@Entity
@Table(name = "sla_policies")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(onlyExplicitlyIncluded = true)
public class SlaPolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @ToString.Include
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "name", nullable = false, length = 100)
    @ToString.Include
    private String name;

    /** {@code null} means this policy applies to all categories. */
    @Column(name = "category_id")
    private UUID categoryId;

    /** {@code null} means this policy applies to all priorities. */
    @Enumerated(EnumType.STRING)
    @Column(name = "priority", length = 20)
    private Priority priority;

    @Column(name = "first_response_hours", nullable = false)
    @Builder.Default
    private int firstResponseHours = 4;

    @Column(name = "resolution_hours", nullable = false)
    @Builder.Default
    private int resolutionHours = 24;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
