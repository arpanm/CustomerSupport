package in.supporthub.faq.integration;

import in.supporthub.faq.FaqServiceApplication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.Embedding;
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
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Integration tests for FAQ CRUD operations and search.
 *
 * <p>The OpenAI embedding model is mocked via {@code @MockBean} on {@link EmbeddingModel}
 * to avoid real API calls. The mock returns a fixed 1536-dimensional zero vector.
 *
 * <p>Uses Testcontainers for PostgreSQL (with pgvector) only; Kafka is included
 * for FAQ event publishing.
 */
@SpringBootTest(
        classes = FaqServiceApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
class FaqCrudIT {

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
        // Disable OpenAI key requirement — EmbeddingModel is mocked
        registry.add("spring.ai.openai.api-key", () -> "test-mock-key");
    }

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    /**
     * Mock OpenAI embeddings to return a fixed 1536-dimensional zero vector,
     * so FAQ creation and semantic search work without a real API key.
     */
    @MockBean
    private EmbeddingModel embeddingModel;

    private HttpHeaders headers;
    private HttpHeaders adminHeaders;

    @BeforeEach
    void setUp() {
        headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Tenant-ID", TENANT_ID);
        headers.set("X-User-Id", USER_ID);

        adminHeaders = new HttpHeaders();
        adminHeaders.setContentType(MediaType.APPLICATION_JSON);
        adminHeaders.set("X-Tenant-ID", TENANT_ID);
        adminHeaders.set("X-User-Id", USER_ID);
        adminHeaders.set("X-User-Role", "ADMIN");

        // Mock embedding to return a 1536-dim zero vector
        float[] zeroVector = new float[1536];
        Embedding embedding = new Embedding(zeroVector, 0);
        EmbeddingResponse mockEmbeddingResponse = new EmbeddingResponse(List.of(embedding));
        when(embeddingModel.embedForResponse(any())).thenReturn(mockEmbeddingResponse);
        when(embeddingModel.embed(any(String.class))).thenReturn(zeroVector);
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private String createFaqBody(String question, String answer) {
        return String.format("""
                {"question": "%s", "answer": "%s"}
                """, question, answer);
    }

    /**
     * POST /api/v1/faqs with ADMIN role should return 201 Created with the FAQ ID.
     */
    @Test
    void createFaq_adminRole_returns201() {
        String body = createFaqBody(
                "How do I track my order?",
                "You can track your order using the tracking link sent to your email.");
        HttpEntity<String> request = new HttpEntity<>(body, adminHeaders);

        ResponseEntity<Map> response = restTemplate.exchange(
                url("/api/v1/faqs"),
                HttpMethod.POST,
                request,
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map<?, ?> body2 = response.getBody();
        assertThat(body2).isNotNull();
        assertThat(body2.get("id")).isNotNull();
    }

    /**
     * GET /api/v1/faqs?page=0&size=10 should return 200 OK with a paginated response.
     */
    @Test
    void listFaqs_returnsPaginated200() {
        ResponseEntity<Map> response = restTemplate.exchange(
                url("/api/v1/faqs?page=0&size=10"),
                HttpMethod.GET,
                new HttpEntity<>(headers),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    /**
     * Create a FAQ then GET it by ID — should return 200 OK with matching question text.
     */
    @Test
    void getFaq_existingId_returns200() {
        // Create
        String question = "What is the return policy for electronics?";
        String body = createFaqBody(
                question,
                "Electronics can be returned within 7 days of delivery if unused and in original packaging.");
        ResponseEntity<Map> createResp = restTemplate.exchange(
                url("/api/v1/faqs"),
                HttpMethod.POST,
                new HttpEntity<>(body, adminHeaders),
                Map.class);
        assertThat(createResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        String id = createResp.getBody().get("id").toString();

        // GET by ID
        ResponseEntity<Map> getResp = restTemplate.exchange(
                url("/api/v1/faqs/" + id),
                HttpMethod.GET,
                new HttpEntity<>(headers),
                Map.class);

        assertThat(getResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getResp.getBody().get("question")).isEqualTo(question);
    }

    /**
     * Create a FAQ then DELETE it with ADMIN role — should return 204 No Content.
     */
    @Test
    void deleteFaq_adminRole_returns204() {
        // Create
        String body = createFaqBody(
                "How do I cancel my subscription?",
                "You can cancel your subscription from the account settings page.");
        ResponseEntity<Map> createResp = restTemplate.exchange(
                url("/api/v1/faqs"),
                HttpMethod.POST,
                new HttpEntity<>(body, adminHeaders),
                Map.class);
        assertThat(createResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String id = createResp.getBody().get("id").toString();

        // DELETE
        ResponseEntity<Void> deleteResp = restTemplate.exchange(
                url("/api/v1/faqs/" + id),
                HttpMethod.DELETE,
                new HttpEntity<>(adminHeaders),
                Void.class);

        assertThat(deleteResp.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    /**
     * Create a FAQ with a known keyword then POST /search — should return 200 with results.
     */
    @Test
    void searchFaq_keywordMatch_returns200() {
        // Create a FAQ with a distinctive keyword
        String keyword = "refundpolicytest";
        String body = createFaqBody(
                "What is the " + keyword + " for damaged items?",
                "The " + keyword + " for damaged items allows full refund within 30 days.");
        restTemplate.exchange(
                url("/api/v1/faqs"),
                HttpMethod.POST,
                new HttpEntity<>(body, adminHeaders),
                Map.class);

        // Search with keyword
        String searchBody = String.format("""
                {"query": "%s", "limit": 5}
                """, keyword);
        ResponseEntity<List> searchResp = restTemplate.exchange(
                url("/api/v1/faqs/search"),
                HttpMethod.POST,
                new HttpEntity<>(searchBody, headers),
                List.class);

        assertThat(searchResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(searchResp.getBody()).isNotNull();
    }
}
