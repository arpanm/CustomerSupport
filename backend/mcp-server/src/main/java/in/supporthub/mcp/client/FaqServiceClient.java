package in.supporthub.mcp.client;

import in.supporthub.mcp.dto.FaqItem;
import in.supporthub.mcp.dto.FaqSearchResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.List;

/**
 * REST client for communicating with the {@code faq-service} (port 8086).
 *
 * <p>Calls include {@code X-Tenant-ID} header for tenant isolation.
 * Calls time out after 10 s. On error an empty {@link FaqSearchResult} is returned
 * (the FAQ search is best-effort — a failure here should not block ticket creation).
 */
@Component
@Slf4j
public class FaqServiceClient {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final WebClient webClient;

    public FaqServiceClient(
            WebClient.Builder webClientBuilder,
            @Value("${faq-service.base-url:http://faq-service:8086}") String baseUrl) {
        this.webClient = webClientBuilder
                .baseUrl(baseUrl)
                .build();
    }

    /**
     * Performs a semantic search over the FAQ knowledge base for the given tenant.
     *
     * <p>On any downstream error, returns an empty result set rather than throwing —
     * the AI agent should still be able to proceed with ticket creation if no FAQ
     * answers are found.
     *
     * @param tenantId UUID of the tenant
     * @param query    the customer's question or issue description
     * @param limit    maximum number of results to return (1–5)
     * @return FAQ search result; never {@code null}; empty list on error
     */
    public FaqSearchResult searchFaq(String tenantId, String query, int limit) {
        int safeLimit = Math.max(1, Math.min(5, limit));
        log.info("FaqServiceClient.searchFaq: tenantId={}, query='{}', limit={}", tenantId, query, safeLimit);

        try {
            return webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/v1/faq/search")
                            .queryParam("q", query)
                            .queryParam("limit", safeLimit)
                            .build())
                    .header("X-Tenant-ID", tenantId)
                    .retrieve()
                    .bodyToMono(FaqSearchResponse.class)
                    .timeout(TIMEOUT)
                    .map(resp -> {
                        List<FaqItem> items = resp.results() == null
                                ? List.of()
                                : resp.results().stream()
                                        .map(r -> new FaqItem(
                                                r.question(),
                                                r.answerExcerpt(),
                                                r.relevanceScore()))
                                        .toList();
                        return new FaqSearchResult(items, items.size());
                    })
                    .blockOptional()
                    .orElseGet(() -> new FaqSearchResult(List.of(), 0));

        } catch (WebClientResponseException ex) {
            log.warn("FaqServiceClient.searchFaq failed: tenantId={}, status={}, body={}",
                    tenantId, ex.getStatusCode(), ex.getResponseBodyAsString());
            return new FaqSearchResult(List.of(), 0);
        } catch (Exception ex) {
            log.error("FaqServiceClient.searchFaq error: tenantId={}, error={}", tenantId, ex.getMessage(), ex);
            return new FaqSearchResult(List.of(), 0);
        }
    }

    // -----------------------------------------------------------------------
    // Internal response DTOs — mirrors faq-service API contracts
    // -----------------------------------------------------------------------

    private record FaqSearchResponse(List<FaqResultItem> results) {}

    private record FaqResultItem(
            String question,
            String answerExcerpt,
            double relevanceScore) {}
}
