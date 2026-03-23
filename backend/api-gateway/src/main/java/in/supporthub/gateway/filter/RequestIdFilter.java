package in.supporthub.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Global filter that ensures every request carries a unique {@code X-Request-Id} header.
 *
 * <p>If the caller already provides an {@code X-Request-Id}, that value is preserved and
 * propagated downstream. If not, a new UUID is generated.
 *
 * <p>The same value is echoed back in the response header so that clients can correlate
 * their requests with server-side logs.
 *
 * <p>This filter runs at {@link Ordered#HIGHEST_PRECEDENCE} to ensure the request ID is
 * available to all downstream filters and log statements.
 */
@Slf4j
@Component
public class RequestIdFilter implements GlobalFilter, Ordered {

    public static final String HEADER_REQUEST_ID = "X-Request-Id";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String existingRequestId = exchange.getRequest().getHeaders().getFirst(HEADER_REQUEST_ID);
        String requestId = StringUtils.hasText(existingRequestId)
                ? existingRequestId
                : UUID.randomUUID().toString();

        ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                .header(HEADER_REQUEST_ID, requestId)
                .build();

        // Echo the request ID back in the response for client-side correlation
        exchange.getResponse().getHeaders().add(HEADER_REQUEST_ID, requestId);

        return chain.filter(exchange.mutate().request(mutatedRequest).build());
    }

    /**
     * Run before all other gateway filters so the request ID is available everywhere.
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
