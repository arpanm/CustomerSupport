package in.supporthub.ticket.integration;

import in.supporthub.ticket.TicketServiceApplication;
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
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for ticket CRUD operations and lifecycle state transitions.
 *
 * <p>Uses Testcontainers for PostgreSQL, Redis, and Kafka.
 * A fixed test tenant UUID is passed in the {@code X-Tenant-ID} header for every request.
 */
@SpringBootTest(
        classes = TicketServiceApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
class TicketCrudIT {

    private static final String TENANT_ID = "550e8400-e29b-41d4-a716-446655440000";
    private static final String USER_ID = "550e8400-e29b-41d4-a716-446655440001";
    private static final String CATEGORY_ID = "660e8400-e29b-41d4-a716-446655440002";

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

    @Container
    static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.0"));

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
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
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Tenant-ID", TENANT_ID);
        headers.set("X-User-Id", USER_ID);
        headers.set("X-User-Role", "AGENT");
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private String createTicketBody(String title, String description) {
        return String.format("""
                {
                  "title": "%s",
                  "description": "%s",
                  "categoryId": "%s",
                  "channel": "WEB",
                  "ticketType": "SUPPORT"
                }
                """, title, description, CATEGORY_ID);
    }

    /**
     * POST /api/v1/tickets with a valid request should return 201 Created
     * with a non-null ticketNumber in the response body.
     */
    @Test
    void createTicket_validRequest_returns201() {
        String body = createTicketBody(
                "Order not delivered after 7 days",
                "I placed an order 7 days ago and it has not been delivered yet. Order ID is 12345.");

        HttpEntity<String> request = new HttpEntity<>(body, headers);

        ResponseEntity<Map> response = restTemplate.exchange(
                url("/api/v1/tickets"),
                HttpMethod.POST,
                request,
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map<?, ?> data = (Map<?, ?>) response.getBody().get("data");
        assertThat(data).isNotNull();
        assertThat(data.get("ticketNumber")).isNotNull();
    }

    /**
     * Create a ticket then GET it by ticket number — should return 200 with matching subject.
     */
    @Test
    void getTicket_existingTicket_returns200() {
        // Create ticket first
        String title = "Wrong item delivered to my address today";
        String body = createTicketBody(
                title,
                "I received a completely different item than what I ordered. The package was delivered today.");
        HttpEntity<String> createReq = new HttpEntity<>(body, headers);
        ResponseEntity<Map> createResp = restTemplate.exchange(
                url("/api/v1/tickets"), HttpMethod.POST, createReq, Map.class);
        assertThat(createResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        Map<?, ?> data = (Map<?, ?>) createResp.getBody().get("data");
        String ticketNumber = (String) data.get("ticketNumber");
        assertThat(ticketNumber).isNotNull();

        // Fetch by ticketNumber
        ResponseEntity<Map> getResp = restTemplate.exchange(
                url("/api/v1/tickets/" + ticketNumber),
                HttpMethod.GET,
                new HttpEntity<>(headers),
                Map.class);

        assertThat(getResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<?, ?> fetchedData = (Map<?, ?>) getResp.getBody().get("data");
        assertThat(fetchedData.get("title")).isEqualTo(title);
    }

    /**
     * Create tickets then GET list with status=OPEN filter — should return 200 with non-empty content.
     */
    @Test
    void getTickets_withStatusFilter_returns200() {
        // Create a ticket to ensure at least one exists
        String body = createTicketBody(
                "Product quality issue with recent purchase",
                "The product I received has a manufacturing defect. I noticed this upon unboxing the item.");
        HttpEntity<String> createReq = new HttpEntity<>(body, headers);
        restTemplate.exchange(url("/api/v1/tickets"), HttpMethod.POST, createReq, Map.class);

        // List with OPEN status filter
        ResponseEntity<Map> listResp = restTemplate.exchange(
                url("/api/v1/tickets?status=OPEN"),
                HttpMethod.GET,
                new HttpEntity<>(headers),
                Map.class);

        assertThat(listResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listResp.getBody()).containsKey("data");
    }

    /**
     * Create a ticket then update the assigneeId — should return 200 with updated assignedAgentId.
     */
    @Test
    void updateTicket_changeAssignee_returns200() {
        // Create
        String body = createTicketBody(
                "Refund not processed after 14 business days",
                "I submitted a refund request 14 business days ago and the amount has not been credited.");
        HttpEntity<String> createReq = new HttpEntity<>(body, headers);
        ResponseEntity<Map> createResp = restTemplate.exchange(
                url("/api/v1/tickets"), HttpMethod.POST, createReq, Map.class);
        assertThat(createResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        Map<?, ?> createdData = (Map<?, ?>) createResp.getBody().get("data");
        String ticketNumber = (String) createdData.get("ticketNumber");

        // Update assignee
        UUID agentId = UUID.randomUUID();
        String updateBody = String.format("""
                {"assignedAgentId": "%s"}
                """, agentId);
        HttpEntity<String> updateReq = new HttpEntity<>(updateBody, headers);
        ResponseEntity<Map> updateResp = restTemplate.exchange(
                url("/api/v1/tickets/" + ticketNumber),
                HttpMethod.PUT,
                updateReq,
                Map.class);

        assertThat(updateResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<?, ?> updatedData = (Map<?, ?>) updateResp.getBody().get("data");
        assertThat(updatedData.get("assignedAgentId")).isEqualTo(agentId.toString());
    }

    /**
     * Full lifecycle: create → update to IN_PROGRESS → resolve should result in RESOLVED status.
     */
    @Test
    void resolveTicket_fromInProgress_returns200() {
        // Create
        String body = createTicketBody(
                "Damaged goods received from latest shipment",
                "The shipment arrived with visible damage to the packaging and the product inside is broken.");
        HttpEntity<String> createReq = new HttpEntity<>(body, headers);
        ResponseEntity<Map> createResp = restTemplate.exchange(
                url("/api/v1/tickets"), HttpMethod.POST, createReq, Map.class);
        assertThat(createResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        Map<?, ?> createdData = (Map<?, ?>) createResp.getBody().get("data");
        String ticketNumber = (String) createdData.get("ticketNumber");

        // Move to IN_PROGRESS
        String progressBody = """
                {"status": "IN_PROGRESS"}
                """;
        HttpEntity<String> progressReq = new HttpEntity<>(progressBody, headers);
        ResponseEntity<Map> progressResp = restTemplate.exchange(
                url("/api/v1/tickets/" + ticketNumber),
                HttpMethod.PUT,
                progressReq,
                Map.class);
        assertThat(progressResp.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Resolve
        String resolveBody = """
                {"resolution": "We have arranged a replacement shipment for the damaged item."}
                """;
        HttpEntity<String> resolveReq = new HttpEntity<>(resolveBody, headers);
        ResponseEntity<Map> resolveResp = restTemplate.exchange(
                url("/api/v1/tickets/" + ticketNumber + "/actions/resolve"),
                HttpMethod.POST,
                resolveReq,
                Map.class);

        assertThat(resolveResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<?, ?> resolvedData = (Map<?, ?>) resolveResp.getBody().get("data");
        assertThat(resolvedData.get("status")).isEqualTo("RESOLVED");
    }

    /**
     * Attempting to REOPEN a ticket that is still OPEN (invalid transition) should return 400.
     */
    @Test
    void invalidTransition_reopenFromOpen_returns400() {
        // Create a ticket (status = OPEN)
        String body = createTicketBody(
                "Invoice amount does not match the purchase price",
                "The invoice I received shows a different amount than what was charged at checkout.");
        HttpEntity<String> createReq = new HttpEntity<>(body, headers);
        ResponseEntity<Map> createResp = restTemplate.exchange(
                url("/api/v1/tickets"), HttpMethod.POST, createReq, Map.class);
        assertThat(createResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        Map<?, ?> createdData = (Map<?, ?>) createResp.getBody().get("data");
        String ticketNumber = (String) createdData.get("ticketNumber");

        // Attempt to reopen (invalid from OPEN state)
        String reopenBody = """
                {"reason": "Trying to reopen from OPEN which should not be allowed"}
                """;
        HttpEntity<String> reopenReq = new HttpEntity<>(reopenBody, headers);
        ResponseEntity<Map> reopenResp = restTemplate.exchange(
                url("/api/v1/tickets/" + ticketNumber + "/actions/reopen"),
                HttpMethod.POST,
                reopenReq,
                Map.class);

        assertThat(reopenResp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
