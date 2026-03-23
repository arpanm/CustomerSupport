package in.supporthub.customer.domain;

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
import lombok.extern.slf4j.Slf4j;
import org.hibernate.annotations.DynamicUpdate;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity representing a customer profile.
 *
 * <p>PII policy:
 * <ul>
 *   <li>Phone is stored as {@code phoneHash} (SHA-256) for lookups and
 *       {@code phoneEncrypted} (AES-256) for display — never raw.</li>
 *   <li>Email follows the same pattern: {@code emailHash} and {@code emailEncrypted}.</li>
 *   <li>NEVER log phoneHash, phoneEncrypted, emailHash, emailEncrypted, or displayName.</li>
 * </ul>
 *
 * <p>The {@code customers} table is owned by auth-service (migrations in auth-service V2).
 * This service reads and partially updates it (name, email, preferences).
 */
@Entity
@Table(name = "customers")
@DynamicUpdate
@Slf4j
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    /** SHA-256 hash of the normalised phone number. Used for lookups. Never log this. */
    @Column(name = "phone_hash", length = 64)
    private String phoneHash;

    /** AES-256 encrypted phone number stored as bytes. Never log this. */
    @Column(name = "phone_encrypted", columnDefinition = "bytea")
    private byte[] phoneEncrypted;

    /** AES-256 encrypted email address stored as bytes. Never log this. */
    @Column(name = "email_encrypted", columnDefinition = "bytea")
    private byte[] emailEncrypted;

    /** SHA-256 hash of the normalised email address. Used for lookups. Never log this. */
    @Column(name = "email_hash", length = 64)
    private String emailHash;

    /** Display name shown in UI. Never log this (PII). */
    @Column(name = "display_name", length = 150)
    private String displayName;

    /** BCP-47 language tag, e.g. "en", "hi". Defaults to "en". */
    @Column(name = "preferred_language", length = 10)
    @Builder.Default
    private String preferredLanguage = "en";

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean isActive = true;

    /** IANA timezone identifier, e.g. "Asia/Kolkata". */
    @Column(name = "timezone", length = 50)
    private String timezone;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;
}
