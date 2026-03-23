package in.supporthub.faq.service;

import in.supporthub.faq.domain.FaqEntry;
import in.supporthub.faq.dto.FaqSearchResult;
import in.supporthub.faq.repository.FaqRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Orchestrates the FAQ self-resolution search flow.
 *
 * <p>Primary path: generates an embedding for the query and runs a pgvector
 * cosine-similarity search against the {@code faq_entries} table.
 *
 * <p>Fallback path: if {@link EmbeddingService} returns an empty array (OpenAI
 * unavailable), falls back to a PostgreSQL {@code ILIKE} keyword search so that
 * customers can still find FAQs during AI service outages.
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SemanticSearchService {

    private static final int ANSWER_EXCERPT_MAX_LENGTH = 300;

    private final EmbeddingService embeddingService;
    private final FaqRepository faqRepository;

    /**
     * Searches published FAQs for the given tenant using semantic similarity.
     * Falls back to keyword search if embedding generation fails.
     *
     * @param tenantId UUID of the tenant — from TenantContextHolder, not user input
     * @param query    natural-language query from the customer
     * @param limit    maximum number of results (1–10)
     * @return ordered list of {@link FaqSearchResult}, nearest/most relevant first
     */
    public List<FaqSearchResult> search(UUID tenantId, String query, int limit) {
        log.info("FAQ search requested: tenantId={}, limit={}", tenantId, limit);

        float[] queryEmbedding = embeddingService.generateQueryEmbedding(query);

        if (queryEmbedding.length == 0) {
            log.warn("Embedding unavailable — falling back to keyword search: tenantId={}", tenantId);
            return keywordSearch(tenantId, query, limit);
        }

        return semanticSearch(tenantId, queryEmbedding, limit);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private List<FaqSearchResult> semanticSearch(UUID tenantId, float[] embedding, int limit) {
        String pgVectorLiteral = toPostgresVectorLiteral(embedding);
        List<FaqEntry> entries = faqRepository.semanticSearch(tenantId, pgVectorLiteral, limit);

        log.debug("Semantic search returned {} results: tenantId={}", entries.size(), tenantId);

        return entries.stream()
                .map(entry -> toSearchResult(entry, 1.0))
                .collect(Collectors.toList());
    }

    private List<FaqSearchResult> keywordSearch(UUID tenantId, String query, int limit) {
        List<FaqEntry> entries = faqRepository.keywordSearch(tenantId, query, limit);

        log.debug("Keyword fallback returned {} results: tenantId={}", entries.size(), tenantId);

        return entries.stream()
                .map(entry -> toSearchResult(entry, 0.0))
                .collect(Collectors.toList());
    }

    private FaqSearchResult toSearchResult(FaqEntry entry, double similarityScore) {
        String excerpt = entry.getAnswer();
        if (excerpt != null && excerpt.length() > ANSWER_EXCERPT_MAX_LENGTH) {
            excerpt = excerpt.substring(0, ANSWER_EXCERPT_MAX_LENGTH) + "…";
        }
        return new FaqSearchResult(entry.getId(), entry.getQuestion(), excerpt, similarityScore);
    }

    /**
     * Converts a Java {@code float[]} into the PostgreSQL vector literal format:
     * {@code [0.1,0.2,...,0.n]}
     */
    private String toPostgresVectorLiteral(float[] embedding) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append(embedding[i]);
        }
        sb.append("]");
        return sb.toString();
    }
}
