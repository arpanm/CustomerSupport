package in.supporthub.faq.service;

import in.supporthub.faq.domain.FaqEntry;
import in.supporthub.faq.dto.FaqSearchResult;
import in.supporthub.faq.repository.FaqRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Requirements-focused unit tests for the faq-service search and analytics features.
 *
 * <p>Requirements covered (or documented as not yet implemented):
 * <ul>
 *   <li>REQ-AI-EMBED-04 — Similarity search only returns results above threshold 0.75
 *       — {@link Disabled}: threshold filtering not implemented in current
 *       {@link SemanticSearchService}; pgvector query returns all published entries
 *       ordered by distance without a score cut-off.</li>
 *   <li>REQ-AI-EMBED-05 — Hybrid search combines vector + text scores
 *       — {@link Disabled}: hybrid search not implemented; service uses vector-only or
 *       keyword-only paths, never a combined scoring strategy.</li>
 *   <li>REQ-FAQ-BOT-02 — High-confidence match (score &gt; 0.80) returned as top result
 *       — {@link Disabled}: bot confidence scoring not implemented; {@link SemanticSearchService}
 *       assigns a fixed score of 1.0 to all semantic results without real similarity values.</li>
 *   <li>REQ-CUI-FAQ-03 — FAQ feedback (helpful/not-helpful) increments respective counters
 *       — {@link Disabled}: {@code helpfulCount} / {@code notHelpfulCount} fields do not exist
 *       in {@link FaqEntry} and no feedback endpoint / service method is implemented.</li>
 *   <li>REQ-FAQ-ADMIN-05 — FAQ analytics: view count increments on FAQ access
 *       — Implemented via {@link FaqService#incrementViewCount}; fully testable.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("FaqRequirementsTest")
class FaqRequirementsTest {

    // =========================================================================
    // REQ-AI-EMBED-04 — Similarity threshold 0.75
    // =========================================================================

    /**
     * REQ-AI-EMBED-04: Semantic search must only return results whose cosine similarity
     * score is at or above 0.75. Results below the threshold must be silently dropped.
     *
     * <p>NOT IMPLEMENTED: The current {@link SemanticSearchService} delegates entirely to
     * {@code faqRepository.semanticSearch()} which orders by pgvector cosine distance but
     * applies no score filter. There is no threshold constant, no post-query filtering, and
     * no similarity score propagated from the DB query. These tests are disabled until the
     * feature is implemented.
     */
    @Nested
    @DisplayName("REQ-AI-EMBED-04: Similarity search filters results below threshold 0.75")
    class SimilarityThresholdTests {

        @Mock
        private EmbeddingService embeddingService;

        @Mock
        private FaqRepository faqRepository;

        @InjectMocks
        private SemanticSearchService semanticSearchService;

        private UUID tenantId;

        @BeforeEach
        void setUp() {
            tenantId = UUID.randomUUID();
        }

        @Test
        @Disabled("REQ-AI-EMBED-04: Score threshold filtering not yet implemented — "
                + "semanticSearch returns all ordered results without a 0.75 cut-off")
        @DisplayName("REQ-AI-EMBED-04: search — results below 0.75 similarity are excluded from response")
        void search_resultsBelowThreshold_areExcluded() {
            // When implemented: repository should be called with a threshold parameter
            // or the service should filter FaqSearchResult entries where similarityScore < 0.75.
            float[] embedding = new float[1536];
            when(embeddingService.generateQueryEmbedding(anyString())).thenReturn(embedding);

            FaqEntry highSimilarity = FaqEntry.builder()
                    .id(UUID.randomUUID()).tenantId(tenantId)
                    .question("How do I track my order?")
                    .answer("Visit the tracking page.")
                    .isPublished(true).build();

            FaqEntry lowSimilarity = FaqEntry.builder()
                    .id(UUID.randomUUID()).tenantId(tenantId)
                    .question("Completely unrelated topic")
                    .answer("Irrelevant answer.")
                    .isPublished(true).build();

            // Repository stub returns both; the service should filter out lowSimilarity
            when(faqRepository.semanticSearch(eq(tenantId), anyString(), anyInt()))
                    .thenReturn(List.of(highSimilarity, lowSimilarity));

            List<FaqSearchResult> results = semanticSearchService.search(tenantId, "track order", 5);

            // Only the high-similarity result (>= 0.75) should survive
            assertThat(results).hasSize(1);
            assertThat(results.get(0).question()).isEqualTo("How do I track my order?");
            assertThat(results.get(0).similarityScore()).isGreaterThanOrEqualTo(0.75);
        }

        @Test
        @Disabled("REQ-AI-EMBED-04: Score threshold filtering not yet implemented — "
                + "all semantic results currently receive a fixed score of 1.0")
        @DisplayName("REQ-AI-EMBED-04: search — all results below 0.75 returns empty list")
        void search_allResultsBelowThreshold_returnsEmptyList() {
            float[] embedding = new float[1536];
            when(embeddingService.generateQueryEmbedding(anyString())).thenReturn(embedding);

            FaqEntry poorMatch = FaqEntry.builder()
                    .id(UUID.randomUUID()).tenantId(tenantId)
                    .question("Unrelated FAQ")
                    .answer("Not relevant.")
                    .isPublished(true).build();

            when(faqRepository.semanticSearch(eq(tenantId), anyString(), anyInt()))
                    .thenReturn(List.of(poorMatch));

            List<FaqSearchResult> results = semanticSearchService.search(tenantId, "billing", 5);

            // All results fall below 0.75 → empty list expected
            assertThat(results).isEmpty();
        }
    }

    // =========================================================================
    // REQ-AI-EMBED-05 — Hybrid search (vector + text score combination)
    // =========================================================================

    /**
     * REQ-AI-EMBED-05: The search service must combine vector similarity scores with
     * full-text search scores using a weighted hybrid strategy (e.g. RRF or weighted sum).
     *
     * <p>NOT IMPLEMENTED: {@link SemanticSearchService} uses a strict either-or strategy:
     * it calls {@code faqRepository.semanticSearch()} when an embedding is available, or
     * {@code faqRepository.keywordSearch()} as a fallback. There is no code path that
     * queries both simultaneously and merges the scores. These tests are disabled until
     * hybrid search is implemented.
     */
    @Nested
    @DisplayName("REQ-AI-EMBED-05: Hybrid search combines vector and text scores")
    class HybridSearchTests {

        @Mock
        private EmbeddingService embeddingService;

        @Mock
        private FaqRepository faqRepository;

        @InjectMocks
        private SemanticSearchService semanticSearchService;

        private UUID tenantId;

        @BeforeEach
        void setUp() {
            tenantId = UUID.randomUUID();
        }

        @Test
        @Disabled("REQ-AI-EMBED-05: Hybrid search not yet implemented — "
                + "service uses vector-only or keyword-only paths, never a combined scoring strategy")
        @DisplayName("REQ-AI-EMBED-05: search — both semanticSearch and keywordSearch are queried when embedding available")
        void search_hybridPath_queriesBothVectorAndKeyword() {
            float[] embedding = new float[1536];
            when(embeddingService.generateQueryEmbedding(anyString())).thenReturn(embedding);

            when(faqRepository.semanticSearch(eq(tenantId), anyString(), anyInt()))
                    .thenReturn(List.of());
            when(faqRepository.keywordSearch(eq(tenantId), anyString(), anyInt()))
                    .thenReturn(List.of());

            semanticSearchService.search(tenantId, "password reset", 5);

            // Hybrid implementation must call BOTH query methods
            verify(faqRepository).semanticSearch(eq(tenantId), anyString(), anyInt());
            verify(faqRepository).keywordSearch(eq(tenantId), anyString(), anyInt());
        }

        @Test
        @Disabled("REQ-AI-EMBED-05: Hybrid search not yet implemented — "
                + "combined scoring (vector + text) is not present in SemanticSearchService")
        @DisplayName("REQ-AI-EMBED-05: search — result appearing in both paths gets a boosted combined score")
        void search_hybridPath_resultInBothSources_hasCombinedScore() {
            float[] embedding = new float[1536];
            UUID sharedFaqId = UUID.randomUUID();

            FaqEntry sharedEntry = FaqEntry.builder()
                    .id(sharedFaqId).tenantId(tenantId)
                    .question("How do I reset my password?")
                    .answer("Click Forgot Password on the login page.")
                    .isPublished(true).build();

            when(embeddingService.generateQueryEmbedding(anyString())).thenReturn(embedding);
            when(faqRepository.semanticSearch(eq(tenantId), anyString(), anyInt()))
                    .thenReturn(List.of(sharedEntry));
            when(faqRepository.keywordSearch(eq(tenantId), anyString(), anyInt()))
                    .thenReturn(List.of(sharedEntry));

            List<FaqSearchResult> results = semanticSearchService.search(tenantId, "password", 5);

            // Entry present in both result sets should appear once with a combined score > 1.0
            // (when using additive scoring) or otherwise marked as a high-confidence match.
            assertThat(results).hasSize(1);
            assertThat(results.get(0).id()).isEqualTo(sharedFaqId);
            assertThat(results.get(0).similarityScore()).isGreaterThan(1.0);
        }
    }

    // =========================================================================
    // REQ-FAQ-BOT-02 — High-confidence match (score > 0.80) returned as top result
    // =========================================================================

    /**
     * REQ-FAQ-BOT-02: When a search result has a similarity score greater than 0.80 the
     * service must return it as the single top result (bot can auto-answer without human
     * review).
     *
     * <p>NOT IMPLEMENTED: {@link SemanticSearchService} assigns a fixed placeholder score
     * of {@code 1.0} to every result returned from pgvector; no actual cosine similarity
     * value is retrieved from the DB query result set, and no threshold-based "top result"
     * selection logic exists. Tests are disabled until this feature is implemented.
     */
    @Nested
    @DisplayName("REQ-FAQ-BOT-02: High-confidence match (score > 0.80) returned as top result")
    class BotHighConfidenceTests {

        @Mock
        private EmbeddingService embeddingService;

        @Mock
        private FaqRepository faqRepository;

        @InjectMocks
        private SemanticSearchService semanticSearchService;

        private UUID tenantId;

        @BeforeEach
        void setUp() {
            tenantId = UUID.randomUUID();
        }

        @Test
        @Disabled("REQ-FAQ-BOT-02: Real similarity scores not propagated from pgvector query — "
                + "all results assigned a fixed score of 1.0; high-confidence selection not implemented")
        @DisplayName("REQ-FAQ-BOT-02: search — result with score > 0.80 is placed at index 0")
        void search_highConfidenceMatch_isReturnedAsTopResult() {
            float[] embedding = new float[1536];
            when(embeddingService.generateQueryEmbedding(anyString())).thenReturn(embedding);

            // Simulate: DB returns entries; service must enrich with real similarity scores
            FaqEntry bestMatch = FaqEntry.builder()
                    .id(UUID.randomUUID()).tenantId(tenantId)
                    .question("How do I reset my password?")
                    .answer("Click Forgot Password on the login page.")
                    .isPublished(true).build();

            FaqEntry weakMatch = FaqEntry.builder()
                    .id(UUID.randomUUID()).tenantId(tenantId)
                    .question("How do I update my profile?")
                    .answer("Go to settings.")
                    .isPublished(true).build();

            when(faqRepository.semanticSearch(eq(tenantId), anyString(), anyInt()))
                    .thenReturn(List.of(bestMatch, weakMatch));

            List<FaqSearchResult> results = semanticSearchService.search(tenantId, "forgot password", 5);

            assertThat(results).isNotEmpty();
            // Top result must have a real similarity score above the bot threshold
            assertThat(results.get(0).similarityScore()).isGreaterThan(0.80);
            assertThat(results.get(0).question()).isEqualTo("How do I reset my password?");
        }

        @Test
        @Disabled("REQ-FAQ-BOT-02: Real similarity scores not propagated from pgvector query — "
                + "no high-confidence detection or single-result short-circuit implemented")
        @DisplayName("REQ-FAQ-BOT-02: search — no result above 0.80 returns multiple candidates for human review")
        void search_noHighConfidenceMatch_returnsMultipleCandidates() {
            float[] embedding = new float[1536];
            when(embeddingService.generateQueryEmbedding(anyString())).thenReturn(embedding);

            FaqEntry candidate1 = FaqEntry.builder()
                    .id(UUID.randomUUID()).tenantId(tenantId)
                    .question("FAQ 1").answer("Answer 1").isPublished(true).build();
            FaqEntry candidate2 = FaqEntry.builder()
                    .id(UUID.randomUUID()).tenantId(tenantId)
                    .question("FAQ 2").answer("Answer 2").isPublished(true).build();

            when(faqRepository.semanticSearch(eq(tenantId), anyString(), anyInt()))
                    .thenReturn(List.of(candidate1, candidate2));

            List<FaqSearchResult> results = semanticSearchService.search(tenantId, "ambiguous query", 5);

            // All results should be below the high-confidence threshold
            assertThat(results).allMatch(r -> r.similarityScore() <= 0.80);
            // Multiple candidates returned for agent/bot to present as options
            assertThat(results.size()).isGreaterThan(1);
        }
    }

    // =========================================================================
    // REQ-CUI-FAQ-03 — FAQ feedback: helpful / not-helpful counters
    // =========================================================================

    /**
     * REQ-CUI-FAQ-03: Customers can mark a FAQ as helpful or not helpful. Each vote must
     * atomically increment the corresponding counter on the FAQ entry.
     *
     * <p>NOT IMPLEMENTED: {@link FaqEntry} has no {@code helpfulCount} or
     * {@code notHelpfulCount} fields. {@link FaqRepository} has no corresponding
     * {@code incrementHelpfulCount} / {@code incrementNotHelpfulCount} queries.
     * {@link FaqService} exposes no feedback method. Tests are disabled until the
     * feature is implemented.
     */
    @Nested
    @DisplayName("REQ-CUI-FAQ-03: FAQ feedback increments helpful / not-helpful counters")
    class FaqFeedbackTests {

        @Mock
        private FaqRepository faqRepository;

        @Mock
        private EmbeddingService embeddingService;

        @InjectMocks
        private FaqService faqService;

        private UUID tenantId;
        private UUID faqId;

        @BeforeEach
        void setUp() {
            tenantId = UUID.randomUUID();
            faqId = UUID.randomUUID();
        }

        @Test
        @Disabled("REQ-CUI-FAQ-03: helpfulCount field and recordFeedback() method not yet implemented in FaqService / FaqEntry")
        @DisplayName("REQ-CUI-FAQ-03: recordFeedback helpful=true — helpfulCount is incremented")
        void recordFeedback_helpful_incrementsHelpfulCount() {
            // When implemented:
            //   faqService.recordFeedback(tenantId, faqId, true);
            //   verify(faqRepository).incrementHelpfulCount(tenantId, faqId);
            //
            // Or if the service reads the entity first:
            //   ArgumentCaptor<FaqEntry> captor = ArgumentCaptor.forClass(FaqEntry.class);
            //   verify(faqRepository).save(captor.capture());
            //   assertThat(captor.getValue().getHelpfulCount()).isEqualTo(1L);
        }

        @Test
        @Disabled("REQ-CUI-FAQ-03: notHelpfulCount field and recordFeedback() method not yet implemented in FaqService / FaqEntry")
        @DisplayName("REQ-CUI-FAQ-03: recordFeedback helpful=false — notHelpfulCount is incremented")
        void recordFeedback_notHelpful_incrementsNotHelpfulCount() {
            // When implemented:
            //   faqService.recordFeedback(tenantId, faqId, false);
            //   verify(faqRepository).incrementNotHelpfulCount(tenantId, faqId);
        }

        @Test
        @Disabled("REQ-CUI-FAQ-03: helpfulCount / notHelpfulCount fields and recordFeedback() not yet implemented")
        @DisplayName("REQ-CUI-FAQ-03: recordFeedback — cross-tenant feedback is rejected with FaqNotFoundException")
        void recordFeedback_wrongTenant_throwsFaqNotFoundException() {
            // When implemented:
            //   ensure findForTenant() guards the feedback endpoint with the same
            //   tenant isolation as all other mutating methods.
        }
    }

    // =========================================================================
    // REQ-FAQ-ADMIN-05 — FAQ analytics: view count increments on FAQ access
    // =========================================================================

    /**
     * REQ-FAQ-ADMIN-05: Every time a customer views a FAQ entry the view counter must be
     * atomically incremented. The increment must use a direct UPDATE to avoid read-modify-write
     * races.
     *
     * <p>IMPLEMENTED: {@link FaqService#incrementViewCount} delegates to
     * {@link FaqRepository#incrementViewCount}, which uses a {@code @Modifying @Query}.
     */
    @Nested
    @DisplayName("REQ-FAQ-ADMIN-05: View count increments on FAQ access")
    class ViewCountAnalyticsTests {

        @Mock
        private FaqRepository faqRepository;

        @Mock
        private EmbeddingService embeddingService;

        @InjectMocks
        private FaqService faqService;

        private UUID tenantId;
        private UUID faqId;

        @BeforeEach
        void setUp() {
            tenantId = UUID.randomUUID();
            faqId = UUID.randomUUID();
        }

        @Test
        @DisplayName("REQ-FAQ-ADMIN-05: incrementViewCount — delegates to repository with correct tenantId and id")
        void incrementViewCount_delegatesToRepositoryWithCorrectArgs() {
            faqService.incrementViewCount(tenantId, faqId);

            verify(faqRepository).incrementViewCount(tenantId, faqId);
        }

        @Test
        @DisplayName("REQ-FAQ-ADMIN-05: incrementViewCount — uses atomic repository query (no entity read before update)")
        void incrementViewCount_doesNotReadEntityFirst() {
            faqService.incrementViewCount(tenantId, faqId);

            // The service must NOT call findById before incrementing — that would introduce
            // a read-modify-write race condition. Only the @Modifying query should be invoked.
            verify(faqRepository).incrementViewCount(tenantId, faqId);
            // findById must never be called as part of the view-count increment path
            org.mockito.Mockito.verifyNoMoreInteractions(faqRepository);
        }

        @Test
        @DisplayName("REQ-FAQ-ADMIN-05: incrementViewCount — called once per access, not batched")
        void incrementViewCount_calledOncePerAccess() {
            // Simulate three independent page loads for the same FAQ
            faqService.incrementViewCount(tenantId, faqId);
            faqService.incrementViewCount(tenantId, faqId);
            faqService.incrementViewCount(tenantId, faqId);

            org.mockito.Mockito.verify(faqRepository, org.mockito.Mockito.times(3))
                    .incrementViewCount(tenantId, faqId);
        }

        @Test
        @DisplayName("REQ-FAQ-ADMIN-05: incrementViewCount — tenant isolation: different tenants tracked independently")
        void incrementViewCount_differentTenants_trackedIndependently() {
            UUID otherTenant = UUID.randomUUID();

            faqService.incrementViewCount(tenantId, faqId);
            faqService.incrementViewCount(otherTenant, faqId);

            verify(faqRepository).incrementViewCount(tenantId, faqId);
            verify(faqRepository).incrementViewCount(otherTenant, faqId);
        }
    }
}
