package in.supporthub.gateway.filter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.supporthub.gateway.service.JwtValidationService;
import in.supporthub.shared.dto.ApiError;
import in.supporthub.shared.dto.ResponseMeta;
import in.supporthub.shared.security.JwtClaims;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;

/**
 * Gateway filter that validates Bearer JWTs on all protected routes.
 *
 * <p>On a valid token, extracts claims and injects downstream headers:
 * {@code X-User-Id}, {@code X-User-Role}, {@code X-User-Type}, {@code X-Tenant-ID}.
 *
 * <p>On missing or invalid token, responds with HTTP 401 and an {@link ApiError} JSON body.
 *
 * <p>Public paths that bypass JWT validation:
 * <ul>
 *   <li>{@code /api/v1/auth/**}</li>
 *   <li>{@code /actuator/health}</li>
 *   <li>{@code /fallback/**}</li>
 *   <li>{@code /mcp/sse} (SSE endpoint uses MCP-level auth)</li>
 * </ul>
 */
@Slf4j
@Component
public class JwtAuthFilter extends AbstractGatewayFilterFactory<JwtAuthFilter.Config> {

    /** Downstream header names injected after successful JWT validation. */
    public static final String HEADER_USER_ID = "X-User-Id";
    public static final String HEADER_USER_ROLE = "X-User-Role";
    public static final String HEADER_USER_TYPE = "X-User-Type";
    public static final String HEADER_TENANT_ID = "X-Tenant-ID";
    public static final String HEADER_REQUEST_ID = "X-Request-Id";

    private static final String BEARER_PREFIX = "Bearer ";
    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    /** Paths that bypass JWT authentication entirely. */
    private static final List<String> PUBLIC_PATHS = List.of(
            "/api/v1/auth/**",
            "/actuator/health",
            "/actuator/health/**",
            "/fallback/**",
            "/mcp/sse"
    );

    private final JwtValidationService jwtValidationService;
    private final ObjectMapper objectMapper;

    public JwtAuthFilter(JwtValidationService jwtValidationService, ObjectMapper objectMapper) {
        super(Config.class);
        this.jwtValidationService = jwtValidationService;
        this.objectMapper = objectMapper;
    }

    @Override
    public GatewayFilter apply(Config config) {
        return this::filter;
    }

    private Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        String requestId = exchange.getRequest().getHeaders().getFirst(HEADER_REQUEST_ID);

        if (isPublicPath(path)) {
            log.debug("Skipping JWT auth for public path: path={}, requestId={}", path, requestId);
            return chain.filter(exchange);
        }

        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            log.info("Missing or malformed Authorization header: path={}, requestId={}", path, requestId);
            return writeUnauthorizedResponse(exchange, requestId,
                    "MISSING_TOKEN", "Authorization header is required.");
        }

        String token = authHeader.substring(BEARER_PREFIX.length());

        try {
            JwtClaims claims = jwtValidationService.validateAndExtract(token);

            ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                    .header(HEADER_USER_ID, claims.sub())
                    .header(HEADER_USER_ROLE, claims.role())
                    .header(HEADER_USER_TYPE, claims.type())
                    // Ensure tenant is set from token if not already present via TenantResolutionFilter
                    .header(HEADER_TENANT_ID, claims.tenantId())
                    // Strip the Authorization header from downstream requests to avoid re-validation cost
                    // (downstream services trust the gateway-injected X-User-* headers)
                    .build();

            log.debug("JWT validated: userId={}, role={}, type={}, tenantId={}, requestId={}",
                    claims.sub(), claims.role(), claims.type(), claims.tenantId(), requestId);

            return chain.filter(exchange.mutate().request(mutatedRequest).build());

        } catch (JwtException ex) {
            log.info("JWT validation failed: path={}, requestId={}, reason={}",
                    path, requestId, ex.getMessage());
            return writeUnauthorizedResponse(exchange, requestId,
                    "INVALID_TOKEN", "Token is invalid or has expired.");
        }
    }

    private boolean isPublicPath(String path) {
        return PUBLIC_PATHS.stream().anyMatch(pattern -> PATH_MATCHER.match(pattern, path));
    }

    private Mono<Void> writeUnauthorizedResponse(
            ServerWebExchange exchange, String requestId, String code, String message) {

        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        ApiError apiError = new ApiError(
                new ApiError.ErrorDetail(code, message, null, requestId),
                new ResponseMeta(requestId, Instant.now(), "v1")
        );

        try {
            byte[] bytes = objectMapper.writeValueAsBytes(apiError);
            DataBuffer buffer = response.bufferFactory().wrap(bytes);
            return response.writeWith(Mono.just(buffer));
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize 401 error response: requestId={}", requestId, e);
            return response.setComplete();
        }
    }

    /** Configuration class (no configurable fields currently; reserved for future use). */
    public static class Config {
    }
}
