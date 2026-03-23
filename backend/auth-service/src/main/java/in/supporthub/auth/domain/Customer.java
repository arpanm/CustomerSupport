package in.supporthub.auth.domain;

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
 * JPA entity for a customer belonging to a tenant.
 *
 * <p>PII fields (phone, email, name) are stored encrypted via application-level AES-256-GCM
 * encryption. The corresponding {@code _hash} columns are HMAC-SHA256 digests used for
 * look-ups without decrypting the full field.
 *
 * <p>Row-level security is enforced by the {@code tenant_isolation} policy on the DB side.
 * Every query through the repository must include {@code tenantId} to be consistent with RLS.
 */
@Entity
@Table(name = "customers")
@DynamicUpdate
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    /** AES-256-GCM encrypted phone number. Never log or expose raw bytes. */
    @Column(name = "phone_encrypted")
    private byte[] phoneEncrypted;

    /**
     * HMAC-SHA256 digest of the canonical phone number (E.164 format).
     * Used for indexed look-ups.
     */
    @Column(name = "phone_hash", nullable = false)
    private String phoneHash;

    /** AES-256-GCM encrypted email address. May be null. */
    @Column(name = "email_encrypted")
    private byte[] emailEncrypted;

    /** HMAC-SHA256 digest of the email address. May be null. */
    @Column(name = "email_hash")
    private String emailHash;

    /** AES-256-GCM encrypted display name. May be null. */
    @Column(name = "name_encrypted")
    private byte[] nameEncrypted;

    @Column(name = "preferred_language", length = 10)
    @Builder.Default
    private String preferredLanguage = "en";

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean isActive = true;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "total_tickets", nullable = false)
    @Builder.Default
    private int totalTickets = 0;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
