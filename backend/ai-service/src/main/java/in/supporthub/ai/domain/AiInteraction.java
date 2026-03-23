package in.supporthub.ai.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * MongoDB document representing a single AI model interaction (sentiment analysis or resolution
 * suggestion) performed by the ai-service.
 *
 * <p>Used for audit trails, cost tracking (token counts), and observability dashboards.
 * The {@code prompt} field must have PII stripped before storage.
 */
@Document(collection = "ai_interactions")
public class AiInteraction {

    @Id
    private String id;

    @Indexed
    private String tenantId;

    @Indexed
    private String ticketId;

    /** Type of AI interaction performed. */
    private InteractionType interactionType;

    /** The prompt sent to the model — PII must be stripped before storage. */
    private String prompt;

    /** The raw text response from the model. */
    private String response;

    /** Identifier of the model used (e.g., "claude-haiku-4-5-20251001"). */
    private String model;

    /** Number of input/prompt tokens consumed. */
    private int inputTokens;

    /** Number of output/completion tokens generated. */
    private int outputTokens;

    /** Total latency in milliseconds from request to response. */
    private long latencyMs;

    private Instant createdAt;

    /** Type of AI interaction. */
    public enum InteractionType {
        SENTIMENT,
        RESOLUTION_SUGGESTION
    }

    public AiInteraction() {}

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getTicketId() {
        return ticketId;
    }

    public void setTicketId(String ticketId) {
        this.ticketId = ticketId;
    }

    public InteractionType getInteractionType() {
        return interactionType;
    }

    public void setInteractionType(InteractionType interactionType) {
        this.interactionType = interactionType;
    }

    public String getPrompt() {
        return prompt;
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }

    public String getResponse() {
        return response;
    }

    public void setResponse(String response) {
        this.response = response;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public int getInputTokens() {
        return inputTokens;
    }

    public void setInputTokens(int inputTokens) {
        this.inputTokens = inputTokens;
    }

    public int getOutputTokens() {
        return outputTokens;
    }

    public void setOutputTokens(int outputTokens) {
        this.outputTokens = outputTokens;
    }

    public long getLatencyMs() {
        return latencyMs;
    }

    public void setLatencyMs(long latencyMs) {
        this.latencyMs = latencyMs;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
