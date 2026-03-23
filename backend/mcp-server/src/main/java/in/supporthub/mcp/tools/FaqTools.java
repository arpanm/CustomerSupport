package in.supporthub.mcp.tools;

import in.supporthub.mcp.client.FaqServiceClient;
import in.supporthub.mcp.dto.FaqSearchResult;
import in.supporthub.shared.security.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * MCP tool implementation for FAQ knowledge base search.
 *
 * <p>The AI agent MUST search the FAQ before creating a ticket — this tool enables
 * self-service resolution for common questions without raising a support ticket.
 *
 * <p>FAQ search failures return an empty result set (never propagate exceptions to
 * the MCP caller) because the downstream FAQ service is non-critical.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class FaqTools {

    private final FaqServiceClient faqServiceClient;

    @Tool(description = "Search the FAQ knowledge base for answers to common questions. Always search FAQ before creating a ticket — if a relevant answer is found, present it to the customer instead of raising a ticket.")
    public FaqSearchResult searchFaq(
            @ToolParam(description = "The customer's question or issue description") String query,
            @ToolParam(description = "Maximum number of results to return (1-5)") int limit) {

        String tenantId = TenantContextHolder.getTenantId();
        log.info("FaqTools.searchFaq: tenantId={}, limit={}", tenantId, limit);

        try {
            FaqSearchResult result = faqServiceClient.searchFaq(tenantId, query, limit);
            log.info("FaqTools.searchFaq: tenantId={}, resultsFound={}", tenantId, result.totalFound());
            return result;
        } catch (Exception ex) {
            log.error("FaqTools.searchFaq unexpected error: tenantId={}, error={}", tenantId, ex.getMessage(), ex);
            return new FaqSearchResult(List.of(), 0);
        }
    }
}
