package in.supporthub.mcp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * SupportHub MCP Server entry point.
 *
 * <p>Implements the Model Context Protocol (MCP) using Spring AI MCP Server with
 * WebMVC SSE transport. Exposes SupportHub tools (ticket lookup, customer lookup,
 * FAQ search, etc.) to Claude AI agents via short-lived MCP JWTs.
 * Runs on port 8090 (default).
 */
@SpringBootApplication
public class McpServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(McpServerApplication.class, args);
    }
}
