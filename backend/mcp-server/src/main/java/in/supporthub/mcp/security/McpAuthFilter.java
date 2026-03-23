package in.supporthub.mcp.security;

import in.supporthub.shared.security.JwtClaims;
import in.supporthub.shared.security.TenantContextHolder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Servlet filter that validates JWT Bearer tokens for all {@code /mcp/**} paths.
 *
 * <p>Flow:
 * <ol>
 *   <li>Checks if the request path starts with {@code /mcp/}.</li>
 *   <li>Extracts the {@code Authorization: Bearer <token>} header.</li>
 *   <li>Delegates to {@link JwtValidationService} for RS256 validation.</li>
 *   <li>Populates {@link TenantContextHolder} with the validated {@code tenantId}.</li>
 *   <li>On invalid/missing JWT: responds with HTTP 401 and clears tenant context.</li>
 * </ol>
 *
 * <p>The tenant context is always cleared in the {@code finally} block to prevent
 * tenant leakage across virtual-thread requests.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class McpAuthFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private final JwtValidationService jwtValidationService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        String path = request.getRequestURI();

        if (!PATH_MATCHER.match("/mcp/**", path)) {
            filterChain.doFilter(request, response);
            return;
        }

        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            log.warn("MCP auth filter: missing or malformed Authorization header, path={}", path);
            sendUnauthorized(response, "Missing or malformed Authorization header.");
            return;
        }

        String token = authHeader.substring(BEARER_PREFIX.length());

        try {
            JwtClaims claims = jwtValidationService.validate(token);
            String tenantId = claims.tenantId();

            if (tenantId == null || tenantId.isBlank()) {
                log.warn("MCP auth filter: JWT missing tenant_id claim, path={}", path);
                sendUnauthorized(response, "Token is missing required tenant context.");
                return;
            }

            TenantContextHolder.setTenantId(tenantId);
            log.debug("MCP auth filter: JWT validated, tenantId={}, sub={}, path={}", tenantId, claims.sub(), path);

            filterChain.doFilter(request, response);

        } catch (McpAuthException ex) {
            log.warn("MCP auth filter: JWT validation failed, path={}, error={}", path, ex.getMessage());
            sendUnauthorized(response, ex.getMessage());
        } finally {
            TenantContextHolder.clear();
        }
    }

    private void sendUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"error\":\"" + message.replace("\"", "'") + "\"}");
    }
}
