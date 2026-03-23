package in.supporthub.faq.service;

import in.supporthub.faq.domain.FaqEntry;
import in.supporthub.faq.dto.FaqSearchResult;
import in.supporthub.faq.repository.FaqRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link SemanticSearchService}.
 *
 * <p>Tests cover:
 * <ul>
 *   <li>Successful semantic search path (embedding available → native pgvector query)</li>
 *   <li>Keyword fallback when EmbeddingService returns empty array</li>
 *   <li>Empty result handling for both paths</li>
 *   <li>Answer excerpt truncation at 300 characters</li>
 *   <li>Similarity score conventions (1.0 for semantic, 0.0 for keyword fallback)</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SemanticSearchService")
class SemanticSearchServiceTest {

    @Mock
    private EmbeddingService embeddingService;

    @Mock
    private FaqRepository faqRepository;

    @InjectMocks
    private SemanticSearchService semanticSearchService;

    private UUID tenantId;
    private FaqEntry sampleEntry;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        sampleEntry = FaqEntry.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .question("How do I reset my password?")
                .answer("To reset your password, click on the 'Forgot Password' link on the login page.")
                .isPublished(true)
                .build();
    }

    // =========================================================================
    // Semantic search path
    // =========================================================================

    @Test
    @DisplayName("search — embedding available — calls native pgvector query")
    void search_embeddingAvailable_callsSemanticSearch() {
        float[] mockEmbedding = new float[1536];
        mockEmbedding[0] = 0.1f;
        when(embeddingService.generateQueryEmbedding(anyString())).thenReturn(mockEmbedding);
        when(faqRepository.semanticSearch(eq(tenantId), anyString(), eq(5)))
                .thenReturn(List.of(sampleEntry));

        List<FaqSearchResult> results = semanticSearchService.search(tenantId, "password reset", 5);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).question()).isEqualTo("How do I reset my password?");
        assertThat(results.get(0).similarityScore()).isEqualTo(1.0);

        verify(faqRepository).semanticSearch(eq(tenantId), anyString(), eq(5));
        verify(faqRepository, never()).keywordSearch(any(), any(), anyInt());
    }

    @Test
    @DisplayName("search — embedding available — result answer excerpt is truncated to 300 chars")
    void search_embeddingAvailable_answerExcerptTruncated() {
        String longAnswer = "A".repeat(500);
        sampleEntry.setAnswer(longAnswer);

        float[] mockEmbedding = new float[1536];
        when(embeddingService.generateQueryEmbedding(anyString())).thenReturn(mockEmbedding);
        when(faqRepository.semanticSearch(eq(tenantId), anyString(), anyInt()))
                .thenReturn(List.of(sampleEntry));

        List<FaqSearchResult> results = semanticSearchService.search(tenantId, "any query", 3);

        assertThat(results).hasSize(1);
        // excerpt = 300 chars + "…" = 301 chars
        assertThat(results.get(0).answerExcerpt()).hasSize(301);
        assertThat(results.get(0).answerExcerpt()).endsWith("…");
    }

    @Test
    @DisplayName("search — embedding available — empty result set returns empty list")
    void search_embeddingAvailable_emptyResults_returnsEmptyList() {
        float[] mockEmbedding = new float[1536];
        when(embeddingService.generateQueryEmbedding(anyString())).thenReturn(mockEmbedding);
        when(faqRepository.semanticSearch(eq(tenantId), anyString(), anyInt()))
                .thenReturn(List.of());

        List<FaqSearchResult> results = semanticSearchService.search(tenantId, "something obscure", 5);

        assertThat(results).isEmpty();
    }

    // =========================================================================
    // Keyword fallback path
    // =========================================================================

    @Test
    @DisplayName("search — embedding service returns empty array — falls back to keyword search")
    void search_embeddingFails_fallsBackToKeywordSearch() {
        when(embeddingService.generateQueryEmbedding(anyString())).thenReturn(new float[0]);
        when(faqRepository.keywordSearch(eq(tenantId), anyString(), eq(5)))
                .thenReturn(List.of(sampleEntry));

        List<FaqSearchResult> results = semanticSearchService.search(tenantId, "password reset", 5);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).question()).isEqualTo("How do I reset my password?");
        // Keyword fallback results have similarityScore = 0.0
        assertThat(results.get(0).similarityScore()).isEqualTo(0.0);

        verify(faqRepository).keywordSearch(eq(tenantId), anyString(), eq(5));
        verify(faqRepository, never()).semanticSearch(any(), any(), anyInt());
    }

    @Test
    @DisplayName("search — embedding fails — keyword fallback with empty results returns empty list")
    void search_embeddingFails_keywordEmptyResults_returnsEmptyList() {
        when(embeddingService.generateQueryEmbedding(anyString())).thenReturn(new float[0]);
        when(faqRepository.keywordSearch(eq(tenantId), anyString(), anyInt()))
                .thenReturn(List.of());

        List<FaqSearchResult> results = semanticSearchService.search(tenantId, "xkjdhqwkjdhqwkj", 5);

        assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("search — embedding fails — keyword fallback returns multiple results in order")
    void search_embeddingFails_keywordReturnsMultipleResults() {
        FaqEntry entry2 = FaqEntry.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .question("How do I change my email?")
                .answer("Go to account settings and update your email address.")
                .isPublished(true)
                .build();

        when(embeddingService.generateQueryEmbedding(anyString())).thenReturn(new float[0]);
        when(faqRepository.keywordSearch(eq(tenantId), anyString(), eq(5)))
                .thenReturn(List.of(sampleEntry, entry2));

        List<FaqSearchResult> results = semanticSearchService.search(tenantId, "account", 5);

        assertThat(results).hasSize(2);
        assertThat(results.get(0).question()).isEqualTo("How do I reset my password?");
        assertThat(results.get(1).question()).isEqualTo("How do I change my email?");
    }

    // =========================================================================
    // pgvector literal format
    // =========================================================================

    @Test
    @DisplayName("search — pgvector literal is passed in correct [x,y,...] format")
    void search_pgVectorLiteralFormat() {
        float[] mockEmbedding = {0.1f, 0.2f, 0.3f};
        when(embeddingService.generateQueryEmbedding(anyString())).thenReturn(mockEmbedding);
        when(faqRepository.semanticSearch(eq(tenantId), anyString(), anyInt()))
                .thenReturn(List.of());

        semanticSearchService.search(tenantId, "test", 5);

        // Verify that semanticSearch is called with a properly formatted pgvector literal
        verify(faqRepository).semanticSearch(
                eq(tenantId),
                org.mockito.ArgumentMatchers.matches("\\[.*\\]"),
                anyInt()
        );
    }
}
