package in.supporthub.mcp.config;

import in.supporthub.mcp.security.McpAuthFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security configuration for the MCP server.
 *
 * <p>Security rules:
 * <ul>
 *   <li>{@code /actuator/health} — public (liveness/readiness probes).</li>
 *   <li>{@code /mcp/**}         — protected by JWT Bearer auth via {@link McpAuthFilter}.</li>
 *   <li>All other paths         — forbidden by default (deny-all).</li>
 * </ul>
 *
 * <p>Sessions are STATELESS — no {@code HttpSession} is created. JWT validation
 * and tenant context propagation happen entirely in {@link McpAuthFilter}.
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final McpAuthFilter mcpAuthFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers("/actuator/info").permitAll()
                        .requestMatchers("/mcp/**").authenticated()
                        .anyRequest().denyAll())
                .addFilterBefore(mcpAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
