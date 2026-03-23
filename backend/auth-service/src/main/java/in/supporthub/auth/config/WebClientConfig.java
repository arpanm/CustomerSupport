package in.supporthub.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * Provides HTTP client beans used by outbound service calls (e.g., MSG91 SMS API).
 */
@Configuration
public class WebClientConfig {

    /**
     * Shared {@link RestTemplate} instance for outbound HTTP calls.
     *
     * <p>In production, configure timeouts and a connection pool by wrapping this
     * with an {@code HttpComponentsClientHttpRequestFactory}.
     */
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
