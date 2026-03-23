package in.supporthub.ordersync.config;

import in.supporthub.ordersync.controller.TenantContextFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security configuration for the order-sync-service.
 *
 * <p>Security model:
 * <ul>
 *   <li>STATELESS — no server-side session state.</li>
 *   <li>CSRF disabled — all callers are stateless JWT consumers.</li>
 *   <li>Actuator health endpoints are public (liveness/readiness probes).</li>
 *   <li>All other endpoints require an authenticated request
 *       (JWT validated at the API gateway; tenant context propagated via headers).</li>
 *   <li>{@link TenantContextFilter} runs before the security chain to populate
 *       {@link in.supporthub.shared.security.TenantContextHolder}.</li>
 * </ul>
 *
 * <p>JWT signature validation is the API gateway's responsibility. This service trusts
 * the {@code X-Tenant-ID}, {@code X-User-Id}, and {@code X-User-Role} headers set
 * by the gateway and requires that they be present on protected endpoints.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final TenantContextFilter tenantContextFilter;

    public SecurityConfig(TenantContextFilter tenantContextFilter) {
        this.tenantContextFilter = tenantContextFilter;
    }

    /**
     * Configures the security filter chain.
     *
     * <p>Permitted without authentication:
     * <ul>
     *   <li>{@code /actuator/health/**} — liveness/readiness probes</li>
     *   <li>{@code /v3/api-docs/**} — OpenAPI spec</li>
     *   <li>{@code /swagger-ui/**} — Swagger UI assets</li>
     * </ul>
     *
     * <p>All other requests require authentication.
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
                )
                .addFilterBefore(tenantContextFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
