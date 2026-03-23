package in.supporthub.tenant.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * Per-tenant configuration key-value store.
 *
 * <p>Stores arbitrary tenant-specific settings such as branding colours, SLA defaults,
 * feature flag overrides, and notification preferences. Each row is isolated to its tenant
 * via the {@code tenantId} column and the RLS policy on the {@code tenant_configs} table.
 *
 * <p>Example keys: {@code branding.primary_color}, {@code sla.default_response_hours}.
 */
@Entity
@Table(name = "tenant_configs")
@DynamicUpdate
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** FK to the owning tenant — also used by RLS for isolation. */
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    /** Configuration key, e.g. {@code sla.default_response_hours}. */
    @Column(name = "config_key", nullable = false, length = 200)
    private String configKey;

    /** Configuration value stored as text. */
    @Column(name = "config_value", columnDefinition = "TEXT")
    private String configValue;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
