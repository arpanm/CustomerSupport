package in.supporthub.reporting.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.elasticsearch.client.ClientConfiguration;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchConfiguration;

/**
 * Elasticsearch client configuration.
 *
 * <p>Extends {@link ElasticsearchConfiguration} to register the reactive and
 * imperative Elasticsearch infrastructure beans. The connection URI is supplied
 * via the {@code ELASTICSEARCH_URL} environment variable (default: {@code http://localhost:9200}).
 */
@Configuration
public class ElasticsearchConfig extends ElasticsearchConfiguration {

    @Value("${spring.elasticsearch.uris}")
    private String esUri;

    /**
     * Builds the Elasticsearch client configuration pointing to the configured URI.
     *
     * <p>The URI scheme ({@code http://}) is stripped because {@link ClientConfiguration}
     * expects a bare {@code host:port} string.
     *
     * @return client configuration
     */
    @Override
    public ClientConfiguration clientConfiguration() {
        return ClientConfiguration.builder()
                .connectedTo(esUri.replace("http://", "").replace("https://", ""))
                .build();
    }
}
