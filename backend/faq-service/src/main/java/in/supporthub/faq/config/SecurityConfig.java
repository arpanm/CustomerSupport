package in.supporthub.faq.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security configuration for the faq-service.
 *
 * <p>Access control matrix:
 * <pre>
 * Method  Path                          Auth required
 * ------  ----------------------------  ----------------------------
 * GET     /api/v1/faqs                  No  (public listing)
 * GET     /api/v1/faqs/{id}             No  (public read)
 * POST    /api/v1/faqs/search           No  (self-resolution flow)
 * POST    /api/v1/webhooks/strapi       No  (auth via HMAC in handler)
 * POST    /api/v1/faqs                  Yes (ADMIN — enforced in controller)
 * PUT     /api/v1/faqs/{id}             Yes (ADMIN — enforced in controller)
 * DELETE  /api/v1/faqs/{id}             Yes (ADMIN — enforced in controller)
 * POST    /api/v1/faqs/{id}/publish     Yes (ADMIN — enforced in controller)
 * </pre>
 *
 * <p>JWT validation is performed by the API gateway. This service trusts the
 * {@code X-User-Role} header injected by the gateway and performs a secondary
 * role check as defence-in-depth (see {@code FaqController.requireAdminRole}).
 *
 * <p>Sessions are STATELESS. CSRF is disabled — all clients are JWT-based.
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
                        // Public read endpoints
                        .requestMatchers(HttpMethod.GET,
                                "/api/v1/faqs",
                                "/api/v1/faqs/**").permitAll()
                        // Self-resolution search (public)
                        .requestMatchers(HttpMethod.POST, "/api/v1/faqs/search").permitAll()
                        // Strapi webhook — auth handled by HMAC in StrapiWebhookService
                        .requestMatchers(HttpMethod.POST, "/api/v1/webhooks/strapi").permitAll()
                        // Actuator health checks
                        .requestMatchers(
                                "/actuator/health",
                                "/actuator/health/**",
                                "/actuator/info",
                                "/actuator/metrics/**").permitAll()
                        // OpenAPI documentation
                        .requestMatchers(
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html").permitAll()
                        // All other requests require authentication
                        .anyRequest().authenticated()
                );

        return http.build();
    }
}
