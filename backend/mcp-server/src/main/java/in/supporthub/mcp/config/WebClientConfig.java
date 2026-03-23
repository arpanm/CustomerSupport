package in.supporthub.mcp.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * WebClient configuration for downstream service clients.
 *
 * <p>Provides a shared {@link WebClient.Builder} bean configured with sensible
 * defaults (codec limits, content-type negotiation). Individual service clients
 * ({@code TicketServiceClient}, {@code FaqServiceClient}) inject this builder and
 * set their own {@code baseUrl}.
 */
@Configuration
public class WebClientConfig {

    /**
     * Shared {@link WebClient.Builder} with default codec configuration.
     *
     * <p>Increasing the max in-memory size to 2 MB to handle ticket list payloads
     * that may include activity descriptions.
     *
     * @return configured builder
     */
    @Bean
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder()
                .codecs(configurer -> configurer
                        .defaultCodecs()
                        .maxInMemorySize(2 * 1024 * 1024)); // 2 MB
    }
}
