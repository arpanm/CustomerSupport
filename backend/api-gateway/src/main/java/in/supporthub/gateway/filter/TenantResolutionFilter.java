package in.supporthub.gateway.filter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.supporthub.shared.dto.ApiError;
import in.supporthub.shared.dto.ResponseMeta;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;

/**
 * Gateway filter that resolves the tenant context for every request.
 *
 * <p>Resolution strategy (in priority order):
 * <ol>
 *   <li>Use the {@code X-Tenant-ID} header if present and non-blank.</li>
 *   <li>Extract the tenant slug from the subdomain: {@code {slug}.supporthub.in}.</li>
 * </ol>
 *
 * <p>On success, ensures {@code X-Tenant-ID} is set on the mutated downstream request.
 * On failure for protected paths, responds with HTTP 400.
 *
 * <p>Public paths that do not require tenant resolution:
 * <ul>
 *   <li>{@code /actuator/health}</li>
 *   <li>{@code /fallback/**}</li>
 * </ul>
 */
@Slf4j
@Component
public class TenantResolutionFilter extends AbstractGatewayFilterFactory<TenantResolutionFilter.Config> {

    public static final String HEADER_TENANT_ID = "X-Tenant-ID";
    public static final String HEADER_REQUEST_ID = "X-Request-Id";

    /** Paths that do not require a tenant to be present. */
    private static final List<String> TENANT_EXEMPT_PATHS = List.of(
            "/actuator/health",
            "/actuator/health/**",
            "/fallback/**"
    );

    /** Suffix used for subdomain-based tenant resolution. */
    private static final String SUPPORTHUB_DOMAIN_SUFFIX = ".supporthub.in";

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private final ObjectMapper objectMapper;

    public TenantResolutionFilter(ObjectMapper objectMapper) {
        super(Config.class);
        this.objectMapper = objectMapper;
    }

    @Override
    public GatewayFilter apply(Config config) {
        return this::filter;
    }

    private Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        String requestId = exchange.getRequest().getHeaders().getFirst(HEADER_REQUEST_ID);

        if (isTenantExemptPath(path)) {
            return chain.filter(exchange);
        }

        // 1. Try X-Tenant-ID header first
        String tenantId = exchange.getRequest().getHeaders().getFirst(HEADER_TENANT_ID);

        // 2. Fall back to subdomain extraction
        if (!StringUtils.hasText(tenantId)) {
            tenantId = extractTenantFromSubdomain(exchange);
        }

        if (!StringUtils.hasText(tenantId)) {
            log.info("Tenant resolution failed — no X-Tenant-ID header and no recognisable subdomain: "
                    + "path={}, requestId={}", path, requestId);
            return writeBadRequestResponse(exchange, requestId,
                    "MISSING_TENANT", "X-Tenant-ID header is required.");
        }

        final String resolvedTenantId = tenantId;
        log.debug("Tenant resolved: tenantId={}, path={}, requestId={}",
                resolvedTenantId, path, requestId);

        ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                .header(HEADER_TENANT_ID, resolvedTenantId)
                .build();

        return chain.filter(exchange.mutate().request(mutatedRequest).build());
    }

    /**
     * Extracts the tenant slug from the {@code Host} header when the request arrives on a
     * subdomain of {@code supporthub.in}, e.g. {@code acmecorp.supporthub.in}.
     *
     * @param exchange the current server web exchange
     * @return the tenant slug, or {@code null} if the host does not match the expected pattern
     */
    private String extractTenantFromSubdomain(ServerWebExchange exchange) {
        String host = exchange.getRequest().getHeaders().getFirst("Host");
        if (!StringUtils.hasText(host)) {
            return null;
        }
        // Strip port if present
        int colonIndex = host.indexOf(':');
        if (colonIndex > 0) {
            host = host.substring(0, colonIndex);
        }
        if (host.endsWith(SUPPORTHUB_DOMAIN_SUFFIX)) {
            String slug = host.substring(0, host.length() - SUPPORTHUB_DOMAIN_SUFFIX.length());
            // Validate slug: must be non-empty and contain only safe characters
            if (StringUtils.hasText(slug) && slug.matches("^[a-z0-9-]{2,63}$")) {
                return slug;
            }
        }
        return null;
    }

    private boolean isTenantExemptPath(String path) {
        return TENANT_EXEMPT_PATHS.stream().anyMatch(pattern -> PATH_MATCHER.match(pattern, path));
    }

    private Mono<Void> writeBadRequestResponse(
            ServerWebExchange exchange, String requestId, String code, String message) {

        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.BAD_REQUEST);
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
            log.error("Failed to serialize 400 error response: requestId={}", requestId, e);
            return response.setComplete();
        }
    }

    /** Configuration class (reserved for future configurable options). */
    public static class Config {
    }
}
