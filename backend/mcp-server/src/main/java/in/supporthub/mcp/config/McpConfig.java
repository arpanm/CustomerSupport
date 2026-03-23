package in.supporthub.mcp.config;

import org.springframework.context.annotation.Configuration;

/**
 * Spring AI MCP Server configuration.
 *
 * <p>Spring AI MCP auto-configuration handles the following when
 * {@code spring-ai-mcp-server-spring-boot-starter} is on the classpath:
 * <ul>
 *   <li>SSE endpoint registration at {@code /mcp/sse}.</li>
 *   <li>Message endpoint at {@code /mcp/message}.</li>
 *   <li>Tool discovery: all {@code @Component} classes with {@code @Tool}-annotated
 *       methods are automatically registered as MCP tools.</li>
 * </ul>
 *
 * <p>Server identity is configured via {@code application.yml}:
 * <pre>
 * spring.ai.mcp.server.name: supporthub-mcp
 * spring.ai.mcp.server.version: 1.0.0
 * spring.ai.mcp.server.transport: sse
 * </pre>
 *
 * <p>No additional beans are required in this class — Spring AI discovers tools
 * by scanning for {@code @Tool} methods on Spring-managed components.
 */
@Configuration
public class McpConfig {
    // Spring AI MCP auto-configures the SSE endpoint at /mcp/sse.
    // Tools (TicketTools, FaqTools) are registered automatically via @Tool annotation.
}
