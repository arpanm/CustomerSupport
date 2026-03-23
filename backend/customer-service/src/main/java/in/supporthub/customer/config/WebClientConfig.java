package in.supporthub.customer.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Configuration for Spring WebFlux {@link WebClient} used by the customer-service.
 *
 * <p>A shared {@link WebClient.Builder} bean is provided so that individual services
 * (e.g. {@link in.supporthub.customer.service.OrderHistoryService}) can set their own
 * base URL and headers without sharing a mutable client instance.
 *
 * <p>The builder is prototype-scoped by Spring convention — each injection point
 * gets a fresh copy with default settings, which is then customised per call site.
 */
@Configuration
public class WebClientConfig {

    /**
     * Provides a {@link WebClient.Builder} with default codec configuration.
     *
     * <p>Callers should call {@code .baseUrl(...)}, add headers, then call {@code .build()}
     * to obtain a configured client for a single request sequence.
     *
     * @return a pre-configured WebClient builder
     */
    @Bean
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder();
    }
}
