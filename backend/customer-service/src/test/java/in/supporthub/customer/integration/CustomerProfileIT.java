package in.supporthub.customer.integration;

import in.supporthub.customer.CustomerServiceApplication;
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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for customer profile self-service endpoints.
 *
 * <p>The {@code OrderHistoryService} calls the order-sync-service over HTTP; that
 * dependency is mocked via {@code @MockBean} so tests have no dependency on a running
 * order-sync-service.
 *
 * <p>The customer referenced by {@code TEST_USER_ID} must be pre-seeded by the Flyway
 * test migration or an {@code @BeforeEach} SQL insert.
 */
@SpringBootTest(
        classes = CustomerServiceApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
class CustomerProfileIT {

    private static final String TENANT_ID = "550e8400-e29b-41d4-a716-446655440000";
    private static final String USER_ID = "550e8400-e29b-41d4-a716-446655440001";

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("supporthub_test")
                    .withUsername("test")
                    .withPassword("test");

    @Container
    static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.0"));

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        // Point order-sync URL to a non-existent host; OrderHistoryService is mocked
        registry.add("order-sync.base-url", () -> "http://localhost:9999");
    }

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    /**
     * Mock the RestTemplate used by OrderHistoryService to avoid HTTP calls
     * to the order-sync-service during tests.
     */
    @MockBean(name = "orderSyncRestTemplate")
    private RestTemplate orderSyncRestTemplate;

    private HttpHeaders headers;

    @BeforeEach
    void setUpHeaders() {
        headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Tenant-ID", TENANT_ID);
        headers.set("X-User-Id", USER_ID);
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    /**
     * GET /api/v1/customers/me with valid headers should return 200 OK.
     */
    @Test
    void getMyProfile_authenticated_returns200() {
        ResponseEntity<Map> response = restTemplate.exchange(
                url("/api/v1/customers/me"),
                HttpMethod.GET,
                new HttpEntity<>(headers),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    /**
     * PUT /api/v1/customers/me with a displayName change should return 200 and reflect the new name.
     */
    @Test
    void updateProfile_validRequest_returns200() {
        String body = """
                {"displayName": "Test Customer Updated", "preferredLanguage": "en", "timezone": "Asia/Kolkata"}
                """;
        HttpEntity<String> request = new HttpEntity<>(body, headers);

        ResponseEntity<Map> response = restTemplate.exchange(
                url("/api/v1/customers/me"),
                HttpMethod.PUT,
                request,
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<?, ?> data = (Map<?, ?>) response.getBody().get("data");
        assertThat(data).isNotNull();
    }

    /**
     * POST /api/v1/customers/me/addresses with a valid address should return 201 Created.
     */
    @Test
    void addAddress_validRequest_returns201() {
        String body = """
                {
                  "label": "Home",
                  "addressLine1": "123 Main Street",
                  "city": "Mumbai",
                  "state": "Maharashtra",
                  "pincode": "400001"
                }
                """;
        HttpEntity<String> request = new HttpEntity<>(body, headers);

        ResponseEntity<Map> response = restTemplate.exchange(
                url("/api/v1/customers/me/addresses"),
                HttpMethod.POST,
                request,
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map<?, ?> data = (Map<?, ?>) response.getBody().get("data");
        assertThat(data.get("id")).isNotNull();
    }

    /**
     * Create an address then POST to /default — should return 200 OK.
     */
    @Test
    void setDefaultAddress_existingAddress_returns200() {
        // Create an address first
        String body = """
                {
                  "label": "Office",
                  "addressLine1": "456 Business Park",
                  "city": "Pune",
                  "state": "Maharashtra",
                  "pincode": "411001"
                }
                """;
        HttpEntity<String> createReq = new HttpEntity<>(body, headers);
        ResponseEntity<Map> createResp = restTemplate.exchange(
                url("/api/v1/customers/me/addresses"),
                HttpMethod.POST,
                createReq,
                Map.class);
        assertThat(createResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        Map<?, ?> data = (Map<?, ?>) createResp.getBody().get("data");
        String addressId = data.get("id").toString();

        // Set as default
        ResponseEntity<Map> defaultResp = restTemplate.exchange(
                url("/api/v1/customers/me/addresses/" + addressId + "/default"),
                HttpMethod.POST,
                new HttpEntity<>(headers),
                Map.class);

        assertThat(defaultResp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    /**
     * Create an address then DELETE it — should return 204 No Content.
     */
    @Test
    void deleteAddress_existingAddress_returns204() {
        // Create an address first
        String body = """
                {
                  "label": "Temporary",
                  "addressLine1": "789 Delete Street",
                  "city": "Delhi",
                  "state": "Delhi",
                  "pincode": "110001"
                }
                """;
        HttpEntity<String> createReq = new HttpEntity<>(body, headers);
        ResponseEntity<Map> createResp = restTemplate.exchange(
                url("/api/v1/customers/me/addresses"),
                HttpMethod.POST,
                createReq,
                Map.class);
        assertThat(createResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        Map<?, ?> data = (Map<?, ?>) createResp.getBody().get("data");
        String addressId = data.get("id").toString();

        // Delete it
        ResponseEntity<Void> deleteResp = restTemplate.exchange(
                url("/api/v1/customers/me/addresses/" + addressId),
                HttpMethod.DELETE,
                new HttpEntity<>(headers),
                Void.class);

        assertThat(deleteResp.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }
}
