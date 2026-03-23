package in.supporthub.notification.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Security configuration for the notification-service.
 *
 * <p>This service operates behind the API gateway which handles JWT validation and
 * injects trusted headers ({@code X-Tenant-Id}, {@code X-User-Id}).
 * The service itself is STATELESS — no sessions, no CSRF.
 *
 * <p>All traffic reaching this service is assumed to be pre-authenticated at the gateway level.
 * Actuator endpoints are openly accessible for health checks within the cluster network.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/actuator/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/v3/api-docs/**"
                        ).permitAll()
                        .anyRequest().permitAll()  // Gateway enforces JWT; service trusts X-User-Id header
                );

        return http.build();
    }
}
