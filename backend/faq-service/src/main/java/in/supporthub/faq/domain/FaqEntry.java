package in.supporthub.faq.domain;

import io.hypersistence.utils.hibernate.type.array.StringArrayType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Type;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * JPA entity representing a single FAQ entry in the SupportHub platform.
 *
 * <p>Each FAQ entry belongs to a single tenant (multi-tenancy enforced via {@code tenant_id}
 * and PostgreSQL Row-Level Security). FAQs may be sourced from Strapi CMS (identified by
 * {@code strapi_id}) or created manually via the admin API.
 *
 * <p>The {@code embedding} column stores a 1536-dimensional float vector produced by OpenAI's
 * {@code text-embedding-3-small} model. It is used for pgvector cosine-similarity search.
 * If embedding generation fails, the field is left {@code null} and the entry remains
 * keyword-searchable only.
 */
@Entity
@Table(name = "faq_entries")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FaqEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "category_id")
    private UUID categoryId;

    @Column(name = "question", nullable = false, columnDefinition = "TEXT")
    private String question;

    @Column(name = "answer", nullable = false, columnDefinition = "TEXT")
    private String answer;

    /**
     * Text array of tags. Stored as PostgreSQL {@code text[]} using Hypersistence Utils.
     */
    @Type(StringArrayType.class)
    @Column(name = "tags", columnDefinition = "text[]")
    private String[] tags;

    /**
     * Strapi content entry ID. Null for manually created FAQs.
     * Used to correlate webhook payloads with existing entries (upsert logic).
     */
    @Column(name = "strapi_id", length = 100)
    private String strapiId;

    @Column(name = "is_published", nullable = false)
    @Builder.Default
    private boolean isPublished = false;

    @Column(name = "view_count", nullable = false)
    @Builder.Default
    private long viewCount = 0L;

    /**
     * 1536-dimensional embedding vector for pgvector cosine similarity search.
     * Stored as {@code vector(1536)} in PostgreSQL. Null if embedding generation failed.
     */
    @Column(name = "embedding", columnDefinition = "vector(1536)")
    private float[] embedding;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }
}
