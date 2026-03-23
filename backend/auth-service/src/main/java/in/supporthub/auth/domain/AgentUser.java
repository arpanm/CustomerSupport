package in.supporthub.auth.domain;

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
 * JPA entity for an agent user (support agent, team lead, admin, or super-admin).
 *
 * <p>Agents authenticate with email + BCrypt password. ADMIN and SUPER_ADMIN roles additionally
 * require a 2-FA email OTP on login. {@code twoFaSecret} is reserved for future TOTP support.
 *
 * <p>The {@code email} column is stored in plain text for agent accounts (tenant-controlled data)
 * but MUST NEVER appear in log output.
 */
@Entity
@Table(name = "agent_users")
@DynamicUpdate
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentUser {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    /** Agent login email. Never log this value. */
    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    /** BCrypt-hashed password. Never log or expose. */
    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    @Builder.Default
    private AgentRole role = AgentRole.AGENT;

    @Column(name = "team_id")
    private UUID teamId;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean isActive = true;

    @Column(name = "is_available", nullable = false)
    @Builder.Default
    private boolean isAvailable = false;

    @Column(name = "two_fa_enabled", nullable = false)
    @Builder.Default
    private boolean twoFaEnabled = false;

    /** Reserved for future TOTP secret storage. Currently unused. */
    @Column(name = "two_fa_secret")
    private String twoFaSecret;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "password_changed_at")
    private Instant passwordChangedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
