package in.supporthub.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

/**
 * Reactive Spring Security configuration for the API Gateway.
 *
 * <p>JWT validation is handled entirely by {@link in.supporthub.gateway.filter.JwtAuthFilter}
 * as a Spring Cloud Gateway filter, so Spring Security here is configured in a minimal
 * "permit all and disable defaults" mode to avoid double-processing auth logic.
 *
 * <p>Explicitly permitted paths (Spring Security layer):
 * <ul>
 *   <li>{@code /api/v1/auth/**} — authentication endpoints</li>
 *   <li>{@code /actuator/health} — health probe for load balancers</li>
 *   <li>{@code /fallback/**} — circuit breaker fallback endpoints</li>
 * </ul>
 *
 * <p>All other access control (JWT validation, tenant isolation, role checks) is enforced
 * by the gateway filter chain and by individual downstream services.
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
        return http
                // Disable CSRF — this is a stateless JWT-based API gateway
                .csrf(ServerHttpSecurity.CsrfSpec::disable)

                // Disable Spring Security's default form login and HTTP Basic
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)

                // Disable session creation — gateway is stateless
                .requestCache(ServerHttpSecurity.RequestCacheSpec::disable)

                // Authorization: permit health, auth, and fallback paths;
                // all other auth is handled by the gateway filter chain (JwtAuthFilter).
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers("/api/v1/auth/**").permitAll()
                        .pathMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .pathMatchers("/fallback/**").permitAll()
                        .anyExchange().permitAll()
                )
                .build();
    }
}
