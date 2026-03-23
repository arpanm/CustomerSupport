package in.supporthub.ai.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Security configuration for the ai-service.
 *
 * <p>The ai-service runs behind the API gateway which handles JWT validation and injects the
 * {@code X-Tenant-ID} header. This service therefore uses stateless sessions and does not
 * perform its own JWT validation.
 *
 * <p>The actuator health endpoint is permitted without authentication so that Kubernetes probes
 * and load-balancer health checks work without credentials.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * Configures the security filter chain with STATELESS session management.
     * Actuator health is open; all other endpoints require authentication.
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
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                .anyRequest().permitAll() // Gateway enforces auth; service trusts X-Tenant-ID header
            );

        return http.build();
    }
}
