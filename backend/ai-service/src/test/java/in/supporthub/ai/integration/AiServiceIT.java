package in.supporthub.ai.integration;

import in.supporthub.ai.AiServiceApplication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.messages.AssistantMessage;
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

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Integration tests for AI service endpoints.
 *
 * <p>The Anthropic Claude API is mocked via {@code @MockBean} on {@link AnthropicChatModel}
 * to prevent real API calls and associated costs during tests.
 *
 * <p>Uses Testcontainers for MongoDB (interaction storage), Redis (caching), and Kafka (events).
 */
@SpringBootTest(
        classes = AiServiceApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
class AiServiceIT {

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
        // Anthropic API key — will be intercepted by the MockBean
        registry.add("spring.ai.anthropic.api-key", () -> "test-mock-key");
    }

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    /** Intercepts all calls to the Anthropic Claude API. */
    @MockBean
    private AnthropicChatModel anthropicChatModel;

    private HttpHeaders headers;

    @BeforeEach
    void setUpHeadersAndMocks() {
        headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Tenant-ID", TENANT_ID);
        headers.set("X-User-Id", USER_ID);

        // Default mock response: a valid sentiment JSON payload
        String sentimentJson = """
                {"label":"NEUTRAL","score":0.5,"reason":"Test mock response"}
                """;
        AssistantMessage assistantMessage = new AssistantMessage(sentimentJson);
        Generation generation = new Generation(assistantMessage, ChatGenerationMetadata.NULL);
        ChatResponse mockResponse = new ChatResponse(List.of(generation));
        when(anthropicChatModel.call(any(org.springframework.ai.chat.prompt.Prompt.class)))
                .thenReturn(mockResponse);
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    /**
     * POST /api/v1/ai/sentiment with a valid request should return 200 OK
     * with a sentiment result (mocked Anthropic response).
     */
    @Test
    void analyzeSentiment_validRequest_returns200() {
        String ticketId = UUID.randomUUID().toString();
        String body = String.format("""
                {"ticketId": "%s", "text": "I am very unhappy with the service provided."}
                """, ticketId);

        HttpEntity<String> request = new HttpEntity<>(body, headers);

        ResponseEntity<Map> response = restTemplate.exchange(
                url("/api/v1/ai/sentiment"),
                HttpMethod.POST,
                request,
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<?, ?> body2 = response.getBody();
        assertThat(body2).isNotNull();
        assertThat(body2.get("label")).isNotNull();
    }

    /**
     * GET /api/v1/ai/interactions/{ticketId} for a ticket with no interactions
     * should return 200 OK with an empty list.
     */
    @Test
    void getInteractions_noInteractions_returnsEmpty() {
        String ticketId = UUID.randomUUID().toString();

        ResponseEntity<List> response = restTemplate.exchange(
                url("/api/v1/ai/interactions/" + ticketId),
                HttpMethod.GET,
                new HttpEntity<>(headers),
                List.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull().isEmpty();
    }

    /**
     * POST /api/v1/ai/resolution-suggestions with a valid request should return 200 OK
     * with a list of suggestions (mocked Anthropic response).
     */
    @Test
    void resolutionSuggestions_validRequest_returns200() {
        // Override mock to return a resolution suggestions JSON array
        String suggestionsJson = """
                [{"title":"Arrange replacement","body":"We will send a replacement item."},
                 {"title":"Process refund","body":"A full refund will be initiated."}]
                """;
        AssistantMessage assistantMessage = new AssistantMessage(suggestionsJson);
        Generation generation = new Generation(assistantMessage, ChatGenerationMetadata.NULL);
        ChatResponse mockResponse = new ChatResponse(List.of(generation));
        when(anthropicChatModel.call(any(org.springframework.ai.chat.prompt.Prompt.class)))
                .thenReturn(mockResponse);

        String ticketId = UUID.randomUUID().toString();
        String body = String.format("""
                {
                  "ticketId": "%s",
                  "title": "Order not delivered",
                  "description": "My order was not delivered within the promised time frame.",
                  "categorySlug": "order-not-delivered"
                }
                """, ticketId);

        HttpEntity<String> request = new HttpEntity<>(body, headers);

        ResponseEntity<List> response = restTemplate.exchange(
                url("/api/v1/ai/resolution-suggestions"),
                HttpMethod.POST,
                request,
                List.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
    }
}
