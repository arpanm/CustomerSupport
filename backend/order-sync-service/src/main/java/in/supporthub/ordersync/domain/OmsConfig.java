package in.supporthub.ordersync.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Tenant-specific OMS (Order Management System) configuration.
 *
 * <p>Stores the OMS endpoint URL, authentication type, and encrypted API key
 * for a given tenant. Each tenant has at most one active OMS configuration
 * (enforced by the UNIQUE constraint on {@code tenant_id}).
 *
 * <p>The API key is stored encrypted at rest in {@code api_key_encrypted} (AES-256-GCM).
 * The plaintext {@code apiKey} field is transient and never persisted.
 */
@Entity
@Table(name = "oms_configs")
@Getter
@Setter
@NoArgsConstructor
public class OmsConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, unique = true)
    private UUID tenantId;

    @Column(name = "oms_base_url", nullable = false, length = 500)
    private String omsBaseUrl;

    /**
     * Encrypted form of the OMS API key (AES-256-GCM).
     * Use {@code PiiEncryptionService} to encrypt/decrypt.
     */
    @Column(name = "api_key_encrypted")
    private byte[] apiKeyEncrypted;

    /**
     * Authentication type used when calling the OMS.
     * One of: BEARER, API_KEY, BASIC.
     */
    @Column(name = "auth_type", nullable = false, length = 20)
    private String authType = "BEARER";

    /**
     * HTTP header name for the API key (e.g., "Authorization", "X-API-Key").
     */
    @Column(name = "header_name", length = 100)
    private String headerName = "Authorization";

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
