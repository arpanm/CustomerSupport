package in.supporthub.faq.repository;

import in.supporthub.faq.domain.FaqEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link FaqEntry}.
 *
 * <p>Includes a native PostgreSQL query for pgvector cosine-similarity search using the
 * {@code <=>} operator (cosine distance). Results are ordered nearest-first (lowest distance
 * = highest similarity).
 */
@Repository
public interface FaqRepository extends JpaRepository<FaqEntry, UUID> {

    /**
     * Returns all published FAQ entries for the given tenant.
     */
    List<FaqEntry> findByTenantIdAndIsPublishedTrue(UUID tenantId);

    /**
     * Returns published FAQ entries scoped to a specific category within a tenant.
     */
    List<FaqEntry> findByTenantIdAndCategoryIdAndIsPublishedTrue(UUID tenantId, UUID categoryId);

    /**
     * Returns a paginated list of all FAQ entries for a tenant (published and unpublished),
     * optionally filtered by category.
     */
    Page<FaqEntry> findByTenantId(UUID tenantId, Pageable pageable);

    /**
     * Returns a paginated list of FAQ entries filtered by category.
     */
    Page<FaqEntry> findByTenantIdAndCategoryId(UUID tenantId, UUID categoryId, Pageable pageable);

    /**
     * Looks up a FAQ entry by its Strapi content entry ID within a tenant.
     * Used for webhook upsert logic.
     */
    Optional<FaqEntry> findByTenantIdAndStrapiId(UUID tenantId, String strapiId);

    /**
     * pgvector cosine-similarity semantic search.
     *
     * <p>Orders results by cosine distance ({@code <=>} operator) ascending — nearest first.
     * Only published entries with a non-null embedding are considered.
     *
     * @param tenantId  tenant UUID to scope results
     * @param embedding query embedding as a float array (cast to {@code vector} in SQL)
     * @param limit     maximum number of results to return
     * @return ordered list of {@link FaqEntry} objects, nearest first
     */
    @Query(value = """
            SELECT * FROM faq_entries
            WHERE tenant_id = :tenantId
              AND is_published = true
              AND embedding IS NOT NULL
            ORDER BY embedding <=> CAST(:embedding AS vector)
            LIMIT :limit
            """,
            nativeQuery = true)
    List<FaqEntry> semanticSearch(
            @Param("tenantId") UUID tenantId,
            @Param("embedding") String embedding,
            @Param("limit") int limit);

    /**
     * Keyword fallback search — case-insensitive match on question or answer.
     * Used when embedding generation fails.
     */
    @Query(value = """
            SELECT * FROM faq_entries
            WHERE tenant_id = :tenantId
              AND is_published = true
              AND (question ILIKE CONCAT('%', :query, '%') OR answer ILIKE CONCAT('%', :query, '%'))
            LIMIT :limit
            """,
            nativeQuery = true)
    List<FaqEntry> keywordSearch(
            @Param("tenantId") UUID tenantId,
            @Param("query") String query,
            @Param("limit") int limit);

    /**
     * Atomically increments the view count for a single FAQ entry.
     */
    @Modifying
    @Query("UPDATE FaqEntry f SET f.viewCount = f.viewCount + 1 WHERE f.id = :id AND f.tenantId = :tenantId")
    void incrementViewCount(@Param("tenantId") UUID tenantId, @Param("id") UUID id);
}
