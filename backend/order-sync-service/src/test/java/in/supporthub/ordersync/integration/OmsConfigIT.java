package in.supporthub.ordersync.integration;

import in.supporthub.ordersync.OrderSyncServiceApplication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for OMS configuration management.
 *
 * <p>Uses Testcontainers for PostgreSQL (config storage) and Redis (cache).
 * The encryption key is set to a test value via the test profile to avoid
 * the {@code ENCRYPTION_SECRET_KEY} environment variable requirement.
 */
@SpringBootTest(
        classes = OrderSyncServiceApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
class OmsConfigIT {

    private static final String TENANT_ID = "550e8400-e29b-41d4-a716-446655440000";
    private static final String USER_ID = "550e8400-e29b-41d4-a716-446655440001";

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("supporthub_test")
                    .withUsername("test")
                    .withPassword("test");

    @Container
    @SuppressWarnings("resource")
    static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:7-alpine")
                    .withExposedPorts(6379);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        // 32-byte AES-256 key (base64 encoded) for test environment
        registry.add("encryption.secret-key", () -> "dGVzdC1lbmNyeXB0aW9uLWtleS0zMmJ5dGVz");
    }

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    private HttpHeaders adminHeaders;

    @BeforeEach
    void setUpHeaders() {
        adminHeaders = new HttpHeaders();
        adminHeaders.setContentType(MediaType.APPLICATION_JSON);
        adminHeaders.set("X-Tenant-ID", TENANT_ID);
        adminHeaders.set("X-User-Id", USER_ID);
        adminHeaders.set("X-User-Role", "ADMIN");
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    /**
     * POST /api/v1/admin/oms-config with ADMIN role and valid OMS config should return 201 Created.
     */
    @Test
    void createOmsConfig_adminRole_returns201() {
        String body = """
                {
                  "omsBaseUrl": "https://oms.example.com/api/v1",
                  "apiKey": "test-oms-api-key-secret",
                  "authType": "BEARER",
                  "headerName": "Authorization"
                }
                """;
        HttpEntity<String> request = new HttpEntity<>(body, adminHeaders);

        ResponseEntity<Map> response = restTemplate.exchange(
                url("/api/v1/admin/oms-config"),
                HttpMethod.POST,
                request,
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map<?, ?> responseBody = response.getBody();
        assertThat(responseBody).isNotNull();
    }

    /**
     * Create OMS config then GET it — API key must NOT be present in the response.
     */
    @Test
    void getOmsConfig_existingConfig_returns200_withApiKeyHidden() {
        // Create config
        String body = """
                {
                  "omsBaseUrl": "https://oms2.example.com/api/v1",
                  "apiKey": "secret-api-key-should-not-appear",
                  "authType": "API_KEY",
                  "headerName": "X-API-Key"
                }
                """;
        ResponseEntity<Map> createResp = restTemplate.exchange(
                url("/api/v1/admin/oms-config"),
                HttpMethod.POST,
                new HttpEntity<>(body, adminHeaders),
                Map.class);
        assertThat(createResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // GET config
        ResponseEntity<Map> getResp = restTemplate.exchange(
                url("/api/v1/admin/oms-config"),
                HttpMethod.GET,
                new HttpEntity<>(adminHeaders),
                Map.class);

        assertThat(getResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<?, ?> data = (Map<?, ?>) getResp.getBody().get("data");
        assertThat(data).isNotNull();
        // API key MUST NOT be exposed
        assertThat(data.containsKey("apiKey")).isFalse();
        assertThat(data.containsKey("encryptedApiKey")).isFalse();
        // URL and authType should be present
        assertThat(data.get("omsBaseUrl")).isEqualTo("https://oms2.example.com/api/v1");
    }

    /**
     * Create OMS config then DELETE it with ADMIN role — should return 204 No Content.
     */
    @Test
    void deleteOmsConfig_adminRole_returns204() {
        // Create first
        String body = """
                {
                  "omsBaseUrl": "https://oms3.example.com/api/v1",
                  "apiKey": "delete-test-key",
                  "authType": "BEARER",
                  "headerName": "Authorization"
                }
                """;
        restTemplate.exchange(
                url("/api/v1/admin/oms-config"),
                HttpMethod.POST,
                new HttpEntity<>(body, adminHeaders),
                Map.class);

        // DELETE
        ResponseEntity<Void> deleteResp = restTemplate.exchange(
                url("/api/v1/admin/oms-config"),
                HttpMethod.DELETE,
                new HttpEntity<>(adminHeaders),
                Void.class);

        assertThat(deleteResp.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }
}
