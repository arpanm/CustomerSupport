package in.supporthub.reporting.config;

import in.supporthub.reporting.controller.TenantContextFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.preauth.AbstractPreAuthenticatedProcessingFilter;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.security.authentication.PreAuthenticatedAuthenticationProvider;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.lang.NonNull;

import java.io.IOException;
import java.util.List;

/**
 * Spring Security configuration for the reporting-service.
 *
 * <p>The service is STATELESS. JWT validation happens at the API gateway.
 * By the time a request reaches this service, the gateway has already:
 * <ol>
 *   <li>Validated the JWT signature and expiry.</li>
 *   <li>Injected {@code X-Tenant-ID} and {@code X-Role} headers.</li>
 * </ol>
 *
 * <p>This config reads the {@code X-Role} header and builds a Spring Security
 * Authentication object so that {@code @PreAuthorize} role checks work correctly.
 *
 * <p>CSRF is disabled — all clients are stateless JWT consumers.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    /** Header injected by the API gateway containing the user's role. */
    private static final String ROLE_HEADER = "X-Role";

    /**
     * Defines the security filter chain.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(roleHeaderAuthFilter(), UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/actuator/health",
                                "/actuator/health/**",
                                "/actuator/info",
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html"
                        ).permitAll()
                        .anyRequest().authenticated()
                );

        return http.build();
    }

    /**
     * Filter that reads the {@code X-Role} header injected by the gateway and creates a Spring
     * Security {@link Authentication} from it so that {@code @PreAuthorize} annotations work.
     */
    @Bean
    public OncePerRequestFilter roleHeaderAuthFilter() {
        return new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(
                    @NonNull HttpServletRequest request,
                    @NonNull HttpServletResponse response,
                    @NonNull FilterChain chain) throws ServletException, IOException {

                String role = request.getHeader(ROLE_HEADER);

                if (role != null && !role.isBlank()) {
                    // Spring Security roles must be prefixed with ROLE_
                    String springRole = role.startsWith("ROLE_") ? role : "ROLE_" + role;
                    PreAuthenticatedAuthenticationToken auth =
                            new PreAuthenticatedAuthenticationToken(
                                    request.getHeader("X-User-ID"),
                                    "N/A",
                                    List.of(new SimpleGrantedAuthority(springRole))
                            );
                    auth.setAuthenticated(true);
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }

                try {
                    chain.doFilter(request, response);
                } finally {
                    SecurityContextHolder.clearContext();
                }
            }
        };
    }
}
