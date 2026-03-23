package in.supporthub.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security configuration for the auth-service.
 *
 * <p>All auth endpoints are public (they ARE the authentication mechanism).
 * The service itself issues JWTs but does not consume them — JWT validation
 * is the responsibility of each downstream service's security filter.
 *
 * <p>Session management is STATELESS. CSRF is disabled because all clients
 * are stateless JWT consumers (no cookie-based session state for auth decisions).
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * Defines the security filter chain.
     *
     * <p>Permitted paths:
     * <ul>
     *   <li>{@code /api/v1/auth/**} — all customer and agent auth endpoints</li>
     *   <li>{@code /actuator/health} — liveness/readiness probes</li>
     *   <li>{@code /v3/api-docs/**} — OpenAPI spec</li>
     *   <li>{@code /swagger-ui/**} — Swagger UI assets</li>
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
                                "/api/v1/auth/**",
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

    /**
     * BCrypt password encoder used to verify agent passwords.
     * Strength 12 provides a balance between security and latency (~300ms at typical server speeds).
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
