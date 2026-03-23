package in.supporthub.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Gateway-level configuration: CORS policy.
 *
 * <p>Route-specific configuration (circuit breakers, rate limiters, etc.) lives in
 * {@code application.yml}. This class handles beans that require programmatic setup.
 */
@Configuration
public class GatewayConfig {

    @Value("${gateway.cors.allowed-origins:http://localhost:3000,http://localhost:3001,http://localhost:3002}")
    private String allowedOriginsConfig;

    /**
     * CORS filter applied globally before any route processing.
     *
     * <p>Allowed headers include the SupportHub-specific headers ({@code X-Tenant-ID},
     * {@code X-Request-Id}) in addition to the standard {@code Authorization} and
     * {@code Content-Type}.
     */
    @Bean
    public CorsWebFilter corsFilter() {
        CorsConfiguration corsConfig = new CorsConfiguration();

        List<String> allowedOrigins = Arrays.asList(allowedOriginsConfig.split(","));
        corsConfig.setAllowedOrigins(allowedOrigins);

        corsConfig.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "HEAD"));

        corsConfig.setAllowedHeaders(List.of(
                "Authorization",
                "Content-Type",
                "X-Tenant-ID",
                "X-Request-Id",
                "Accept",
                "Origin",
                "Cache-Control"
        ));

        corsConfig.setExposedHeaders(List.of(
                "X-Request-Id",
                "X-RateLimit-Remaining",
                "X-RateLimit-Limit"
        ));

        corsConfig.setAllowCredentials(true);
        corsConfig.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", corsConfig);

        return new CorsWebFilter(source);
    }
}
