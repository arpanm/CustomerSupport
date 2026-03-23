package in.supporthub.ordersync.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * WebClient configuration for outbound OMS HTTP calls.
 *
 * <p>Configures the Netty-backed {@link WebClient.Builder} with:
 * <ul>
 *   <li>Connection timeout: 3 seconds</li>
 *   <li>Read timeout: 5 seconds</li>
 *   <li>Write timeout: 5 seconds</li>
 * </ul>
 *
 * <p>Individual call-level timeouts are also applied via
 * {@code .timeout(Duration.ofSeconds(5))} in {@link in.supporthub.ordersync.service.OmsClientService}
 * to cover the total response time including retries.
 */
@Configuration
public class WebClientConfig {

    private static final int CONNECT_TIMEOUT_MS = 3_000;
    private static final int READ_WRITE_TIMEOUT_SECONDS = 5;

    /**
     * Provides a pre-configured {@link WebClient.Builder} bean.
     *
     * <p>Services that need a WebClient should inject {@link WebClient.Builder}
     * and call {@code .build()} to create an instance — this allows per-request
     * customisation (base URL, headers) without sharing mutable state.
     *
     * @return configured builder
     */
    @Bean
    public WebClient.Builder webClientBuilder() {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, CONNECT_TIMEOUT_MS)
                .responseTimeout(Duration.ofSeconds(READ_WRITE_TIMEOUT_SECONDS))
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(READ_WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(READ_WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)));

        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient));
    }
}
