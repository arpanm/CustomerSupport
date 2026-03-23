package in.supporthub.tenant.domain;

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
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Core tenant entity representing a SupportHub customer organisation.
 *
 * <p>Every tenant is isolated via PostgreSQL Row-Level Security. The {@code tenantId} column
 * is a self-reference (same value as {@code id}) to satisfy the RLS policy pattern used across
 * all services.
 *
 * <p>All queries scoped to a tenant MUST include the {@code tenantId} predicate.
 */
@Entity
@Table(name = "tenants")
@DynamicUpdate
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Tenant {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /**
     * Self-reference for RLS compatibility — always equal to {@code id}.
     * Populated after the entity is first saved.
     */
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    /** URL-friendly unique identifier for the tenant, e.g. {@code acme-corp}. */
    @Column(name = "slug", unique = true, nullable = false, length = 100)
    private String slug;

    /** Human-readable display name for the tenant. */
    @Column(name = "name", nullable = false, length = 255)
    private String name;

    /** Subscription plan tier. */
    @Enumerated(EnumType.STRING)
    @Column(name = "plan_type", nullable = false, length = 50)
    private PlanType planType;

    /** Current lifecycle status. */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    @Builder.Default
    private TenantStatus status = TenantStatus.PENDING;

    /** IANA timezone identifier, e.g. {@code Asia/Kolkata}. */
    @Column(name = "timezone", nullable = false, length = 100)
    @Builder.Default
    private String timezone = "Asia/Kolkata";

    /** BCP 47 locale tag, e.g. {@code en-IN}. */
    @Column(name = "locale", nullable = false, length = 20)
    @Builder.Default
    private String locale = "en-IN";

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
