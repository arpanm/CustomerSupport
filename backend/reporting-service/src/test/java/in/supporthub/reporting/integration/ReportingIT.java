package in.supporthub.reporting.integration;

import in.supporthub.reporting.ReportingServiceApplication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for the reporting and analytics API.
 *
 * <p>Uses Testcontainers for Elasticsearch (primary read model store),
 * Redis (cache / idempotency), and Kafka (event consumption).
 *
 * <p>All endpoints use {@code @PreAuthorize} for role checks; the test profile
 * disables Spring Security to allow unauthenticated test requests to reach the controller.
 * The {@code X-Tenant-ID} header is still required for tenant context population.
 */
@SpringBootTest(
        classes = ReportingServiceApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
class ReportingIT {

    private static final String TENANT_ID = "550e8400-e29b-41d4-a716-446655440000";
    private static final String USER_ID = "550e8400-e29b-41d4-a716-446655440001";

    @Container
    @SuppressWarnings("resource")
    static final ElasticsearchContainer ELASTICSEARCH =
            new ElasticsearchContainer(
                    DockerImageName.parse("docker.elastic.co/elasticsearch/elasticsearch:8.13.0"))
                    .withEnv("discovery.type", "single-node")
                    .withEnv("xpack.security.enabled", "false")
                    .withEnv("ES_JAVA_OPTS", "-Xms256m -Xmx256m");

    @Container
    @SuppressWarnings("resource")
    static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:7-alpine")
                    .withExposedPorts(6379);

    @Container
    static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.0"));

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.elasticsearch.uris",
                () -> "http://" + ELASTICSEARCH.getHost() + ":" + ELASTICSEARCH.getMappedPort(9200));
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    private HttpHeaders headers;

    @BeforeEach
    void setUpHeaders() {
        headers = new HttpHeaders();
        headers.set("X-Tenant-ID", TENANT_ID);
        headers.set("X-User-Id", USER_ID);
        headers.set("X-User-Role", "ADMIN");
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    /**
     * GET /api/v1/reports/dashboard with no data should return 200 OK
     * with all-zero or empty summary metrics.
     */
    @Test
    void getDashboardSummary_noData_returns200() {
        ResponseEntity<Map> response = restTemplate.exchange(
                url("/api/v1/reports/dashboard"),
                HttpMethod.GET,
                new HttpEntity<>(headers),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).containsKey("data");
    }

    /**
     * GET /api/v1/reports/tickets/trend?granularity=daily with no data
     * should return 200 OK with an empty or zero-filled list.
     */
    @Test
    void getTrends_noData_returns200() {
        ResponseEntity<Map> response = restTemplate.exchange(
                url("/api/v1/reports/tickets/trend?granularity=daily"),
                HttpMethod.GET,
                new HttpEntity<>(headers),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
    }

    /**
     * GET /api/v1/reports/agents/performance with no data
     * should return 200 OK with an empty list.
     */
    @Test
    void getAgentMetrics_noData_returns200() {
        ResponseEntity<Map> response = restTemplate.exchange(
                url("/api/v1/reports/agents/performance"),
                HttpMethod.GET,
                new HttpEntity<>(headers),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
    }
}
