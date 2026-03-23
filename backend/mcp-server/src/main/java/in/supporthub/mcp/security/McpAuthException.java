package in.supporthub.mcp.security;

/**
 * Thrown by {@link JwtValidationService} when JWT validation fails.
 * Caught by {@link McpAuthFilter} to return HTTP 401.
 */
public class McpAuthException extends RuntimeException {

    public McpAuthException(String message) {
        super(message);
    }
}
