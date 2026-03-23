package in.supporthub.mcp.dto;

import java.util.List;

/**
 * Result returned by the {@code search_faq} MCP tool.
 *
 * @param results    list of matching FAQ items ordered by relevance (highest first)
 * @param totalFound number of results actually returned
 */
public record FaqSearchResult(
        List<FaqItem> results,
        int totalFound) {
}
