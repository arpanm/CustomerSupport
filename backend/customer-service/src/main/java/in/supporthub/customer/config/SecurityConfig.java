package in.supporthub.customer.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security configuration for the customer-service.
 *
 * <p>The customer-service is an internal service: all requests reaching it have already
 * been authenticated and tenant-resolved by the API gateway (which validates JWTs and
 * injects {@code X-Tenant-ID} and {@code X-User-Id} headers).
 *
 * <p>Security strategy:
 * <ul>
 *   <li>Session management: STATELESS (no HTTP sessions, no cookies).</li>
 *   <li>CSRF: disabled — all clients are JWT consumers, not browser-form users.</li>
 *   <li>{@code /actuator/health} — permitted for liveness/readiness probes.</li>
 *   <li>All other paths ({@code /api/**}) — require authenticated requests.
 *       In the internal network the gateway enforces JWT; here we require at minimum
 *       that the request was authenticated (i.e. the gateway is the only caller).</li>
 * </ul>
 *
 * <p>In a Kubernetes environment the network policy restricts direct access to this
 * service to the api-gateway pod only, providing defence in depth.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * Defines the security filter chain for the customer-service.
     *
     * <p>Permitted without authentication:
     * <ul>
     *   <li>{@code /actuator/health} — Kubernetes liveness/readiness probe</li>
     *   <li>{@code /actuator/health/**} — detailed health sub-paths</li>
     *   <li>{@code /v3/api-docs/**} — OpenAPI spec (internal tooling)</li>
     *   <li>{@code /swagger-ui/**} — Swagger UI assets (internal tooling)</li>
     * </ul>
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/actuator/health",
                                "/actuator/health/**",
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html"
                        ).permitAll()
                        .anyRequest().authenticated()
                );

        return http.build();
    }
}
