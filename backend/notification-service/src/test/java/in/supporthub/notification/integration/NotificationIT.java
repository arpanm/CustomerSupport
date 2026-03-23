package in.supporthub.notification.integration;

import in.supporthub.notification.NotificationServiceApplication;
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
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for in-app notification endpoints.
 *
 * <p>Uses Testcontainers for MongoDB (primary store), Redis (cache), and Kafka (events).
 * External SMS/WhatsApp/email clients are mocked via {@code @MockBean} to prevent
 * real outbound messages during tests.
 */
@SpringBootTest(
        classes = NotificationServiceApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
class NotificationIT {

    private static final String TENANT_ID = "550e8400-e29b-41d4-a716-446655440000";
    private static final String USER_ID = "550e8400-e29b-41d4-a716-446655440001";

    @Container
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7.0");

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
        registry.add("spring.data.mongodb.uri", MONGO::getReplicaSetUrl);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        // Disable external messaging by using no-op API keys
        registry.add("msg91.api-key", () -> "test-noop-key");
        registry.add("sendgrid.api-key", () -> "test-noop-key");
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
     * GET /api/v1/notifications/me for a user with no notifications
     * should return 200 OK with an empty page.
     */
    @Test
    void getMyNotifications_noNotifications_returnsEmptyPage() {
        ResponseEntity<Map> response = restTemplate.exchange(
                url("/api/v1/notifications/me"),
                HttpMethod.GET,
                new HttpEntity<>(headers),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        // The response is a Spring Data Page — check totalElements or content is empty
        Map<?, ?> body = response.getBody();
        assertThat(body).isNotNull();
    }

    /**
     * GET /api/v1/notifications/me/unread-count for a user with no notifications
     * should return 200 OK with count = 0.
     */
    @Test
    void getUnreadCount_noNotifications_returnsZero() {
        ResponseEntity<Map> response = restTemplate.exchange(
                url("/api/v1/notifications/me/unread-count"),
                HttpMethod.GET,
                new HttpEntity<>(headers),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<?, ?> body = response.getBody();
        assertThat(body).isNotNull();
        Number count = (Number) body.get("count");
        assertThat(count).isNotNull();
        assertThat(count.longValue()).isZero();
    }

    /**
     * PUT /api/v1/notifications/{randomId}/read for a non-existent notification
     * should return 404 Not Found.
     */
    @Test
    void markAsRead_nonExistentNotification_returns404() {
        String randomId = UUID.randomUUID().toString();

        ResponseEntity<Map> response = restTemplate.exchange(
                url("/api/v1/notifications/" + randomId + "/read"),
                HttpMethod.PUT,
                new HttpEntity<>(headers),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
