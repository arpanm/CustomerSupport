package in.supporthub.tenant.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Security configuration for the tenant-service.
 *
 * <p>The tenant-service runs behind the API gateway which handles JWT validation and injects
 * the {@code X-Tenant-ID} and {@code X-User-Role} headers. This service uses STATELESS sessions
 * and enforces role-based access via those headers.
 *
 * <p>Public routes (no authentication required):
 * <ul>
 *   <li>{@code GET /api/v1/tenants/**} — slug-based tenant resolution used by the gateway</li>
 *   <li>{@code /actuator/health}, {@code /actuator/info} — Kubernetes liveness/readiness probes</li>
 * </ul>
 *
 * <p>Admin routes:
 * <ul>
 *   <li>{@code POST /api/v1/admin/tenants} — SUPER_ADMIN only</li>
 *   <li>{@code PATCH /api/v1/admin/tenants/*/status} — SUPER_ADMIN only</li>
 *   <li>{@code GET, PUT /api/v1/admin/tenants/**} — ADMIN or SUPER_ADMIN</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * Configures the security filter chain.
     *
     * <p>CSRF is disabled because this is a stateless REST service behind the gateway.
     * Session creation is set to STATELESS to prevent accidental server-side session usage.
     *
     * @param http the HttpSecurity builder
     * @return the configured SecurityFilterChain
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Public actuator probes
                .requestMatchers("/actuator/health", "/actuator/info", "/actuator/prometheus")
                    .permitAll()
                // Public slug-resolution endpoint (used by gateway before tenant context is known)
                .requestMatchers(HttpMethod.GET, "/api/v1/tenants/**")
                    .permitAll()
                // SUPER_ADMIN-only: create tenant and change status
                .requestMatchers(HttpMethod.POST, "/api/v1/admin/tenants")
                    .hasRole("SUPER_ADMIN")
                .requestMatchers(HttpMethod.PATCH, "/api/v1/admin/tenants/*/status")
                    .hasRole("SUPER_ADMIN")
                // ADMIN or SUPER_ADMIN: read and config update
                .requestMatchers("/api/v1/admin/tenants/**")
                    .hasAnyRole("ADMIN", "SUPER_ADMIN")
                // All other requests require authentication
                .anyRequest().authenticated()
            );

        return http.build();
    }
}
