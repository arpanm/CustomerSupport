package in.supporthub.auth.integration;

import in.supporthub.auth.AuthServiceApplication;
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
 * Integration tests for the customer OTP authentication flow.
 *
 * <p>Uses Testcontainers for PostgreSQL and Redis. External SMS (MSG91) is disabled
 * via the test profile — the OTP service falls back gracefully.
 */
@SpringBootTest(
        classes = AuthServiceApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
class CustomerAuthIT {

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
    }

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

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
     * POST /api/v1/auth/otp/send with a valid E.164 phone number should return 200 OK.
     * The SMS gateway is mocked via test profile so no real SMS is sent.
     */
    @Test
    void sendOtp_validPhone_returns200() {
        String body = """
                {"phone": "+919876543210"}
                """;
        HttpEntity<String> request = new HttpEntity<>(body, headers);

        ResponseEntity<Map> response = restTemplate.exchange(
                url("/api/v1/auth/otp/send"),
                HttpMethod.POST,
                request,
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    /**
     * POST /api/v1/auth/otp/verify with an incorrect OTP should return 401 or 400.
     */
    @Test
    void verifyOtp_invalidOtp_returns401Or400() {
        String body = """
                {"phone": "+919876543210", "otp": "000000"}
                """;
        HttpEntity<String> request = new HttpEntity<>(body, headers);

        ResponseEntity<Map> response = restTemplate.exchange(
                url("/api/v1/auth/otp/verify"),
                HttpMethod.POST,
                request,
                Map.class);

        assertThat(response.getStatusCode().value())
                .as("Expect 401 or 400 for invalid OTP")
                .isIn(HttpStatus.UNAUTHORIZED.value(), HttpStatus.BAD_REQUEST.value());
    }

    /**
     * POST /api/v1/auth/refresh without a refresh-token cookie should return 401 or 400.
     */
    @Test
    void refreshToken_withoutToken_returns401Or400() {
        HttpEntity<String> request = new HttpEntity<>("{}", headers);

        ResponseEntity<Map> response = restTemplate.exchange(
                url("/api/v1/auth/refresh"),
                HttpMethod.POST,
                request,
                Map.class);

        assertThat(response.getStatusCode().value())
                .as("Expect 401 or 400 when refresh token cookie is absent")
                .isIn(HttpStatus.UNAUTHORIZED.value(), HttpStatus.BAD_REQUEST.value());
    }

    /**
     * POST /api/v1/auth/logout without a refresh-token cookie should return 401 or 400.
     * The endpoint is public but requires the cookie to identify the session.
     */
    @Test
    void logout_withoutToken_returns401Or400() {
        HttpEntity<String> request = new HttpEntity<>(null, headers);

        ResponseEntity<Map> response = restTemplate.exchange(
                url("/api/v1/auth/logout"),
                HttpMethod.POST,
                request,
                Map.class);

        assertThat(response.getStatusCode().value())
                .as("Expect 401 or 400 when refresh token cookie is absent on logout")
                .isIn(HttpStatus.UNAUTHORIZED.value(), HttpStatus.BAD_REQUEST.value());
    }
}
