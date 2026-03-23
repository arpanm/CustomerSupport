package in.supporthub.gateway.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import reactor.core.publisher.Mono;

/**
 * Rate limiter configuration for the API Gateway.
 *
 * <p>Two key resolvers are provided:
 * <ul>
 *   <li>{@link #ipKeyResolver()} — limits by client remote IP address (used on public routes
 *       such as {@code /api/v1/auth/**}).</li>
 *   <li>{@link #tenantKeyResolver()} — limits by {@code X-Tenant-ID} header (used on
 *       authenticated routes to enforce per-tenant quotas).</li>
 * </ul>
 *
 * <p>Rate limits (configurable via {@code gateway.rate-limit.*} properties):
 * <ul>
 *   <li>Per IP: 100 req/s, burst 200</li>
 *   <li>Per tenant: 1000 req/min (~16 req/s), burst 2000</li>
 * </ul>
 */
@Slf4j
@Configuration
public class RateLimiterConfig {

    @Value("${gateway.rate-limit.ip.replenish-rate:100}")
    private int ipReplenishRate;

    @Value("${gateway.rate-limit.ip.burst-capacity:200}")
    private int ipBurstCapacity;

    @Value("${gateway.rate-limit.tenant.replenish-rate:1000}")
    private int tenantReplenishRate;

    @Value("${gateway.rate-limit.tenant.burst-capacity:2000}")
    private int tenantBurstCapacity;

    /**
     * Resolves the rate-limit key by the client's remote IP address.
     *
     * <p>Falls back to {@code "unknown"} when the remote address cannot be determined
     * (e.g., behind a proxy without a forwarded-for header).
     */
    @Bean
    public KeyResolver ipKeyResolver() {
        return exchange -> {
            var remoteAddress = exchange.getRequest().getRemoteAddress();
            if (remoteAddress != null) {
                return Mono.just(remoteAddress.getAddress().getHostAddress());
            }
            // Fallback — apply a shared bucket for unknown origins
            return Mono.just("unknown");
        };
    }

    /**
     * Resolves the rate-limit key by tenant ID from the {@code X-Tenant-ID} header.
     *
     * <p>Falls back to {@code "anonymous"} for requests that have not yet been assigned
     * a tenant (e.g., during auth flows before the tenant is identified).
     */
    @Bean
    public KeyResolver tenantKeyResolver() {
        return exchange -> {
            String tenantId = exchange.getRequest().getHeaders().getFirst("X-Tenant-ID");
            if (tenantId != null && !tenantId.isBlank()) {
                return Mono.just("tenant:" + tenantId);
            }
            return Mono.just("tenant:anonymous");
        };
    }

    /**
     * Default Redis rate limiter for IP-based limiting on public endpoints.
     */
    @Bean
    @Primary
    public RedisRateLimiter defaultRateLimiter() {
        return new RedisRateLimiter(ipReplenishRate, ipBurstCapacity);
    }

    /**
     * Redis rate limiter for per-tenant limiting on authenticated endpoints.
     */
    @Bean
    public RedisRateLimiter tenantRateLimiter() {
        return new RedisRateLimiter(tenantReplenishRate, tenantBurstCapacity);
    }
}
