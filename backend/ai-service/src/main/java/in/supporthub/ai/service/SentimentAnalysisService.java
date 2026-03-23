package in.supporthub.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.supporthub.ai.domain.AiInteraction;
import in.supporthub.ai.domain.AiInteraction.InteractionType;
import in.supporthub.ai.dto.SentimentResult;
import in.supporthub.ai.repository.AiInteractionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * Performs sentiment analysis on customer support ticket text using Claude Haiku.
 *
 * <p>PII is stripped from the text before sending to the model and before persisting in MongoDB.
 * On any failure (API error, malformed JSON), a neutral fallback result is returned — this service
 * never throws to its callers.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SentimentAnalysisService {

    private static final SentimentResult FALLBACK_RESULT =
            new SentimentResult("neutral", 0.0, "analysis failed");

    private final AnthropicChatModel anthropicChatModel;
    private final AiInteractionRepository aiInteractionRepository;
    private final PiiStripper piiStripper;
    private final ObjectMapper objectMapper;

    @Value("${anthropic.sentiment-model:claude-haiku-4-5-20251001}")
    private String sentimentModel;

    /**
     * Analyses the sentiment of the given ticket text.
     *
     * @param tenantId the owning tenant UUID (for multi-tenancy and logging)
     * @param ticketId the ticket UUID (for correlation)
     * @param text     combined title + description text (PII will be stripped internally)
     * @return a {@link SentimentResult} — never null, falls back to neutral on failure
     */
    public SentimentResult analyzeSentiment(String tenantId, String ticketId, String text) {
        String cleanText = piiStripper.strip(text);

        String promptText = "Analyze the sentiment of this customer support message. "
                + "Respond with ONLY a JSON object: "
                + "{\"label\": \"very_negative|negative|neutral|positive|very_positive\", "
                + "\"score\": -1.0 to 1.0, "
                + "\"reason\": \"brief explanation max 100 chars\"}. "
                + "Message: " + cleanText;

        long startMs = System.currentTimeMillis();
        ChatResponse chatResponse = null;

        try {
            ChatClient chatClient = ChatClient.builder(anthropicChatModel)
                    .defaultOptions(AnthropicChatOptions.builder()
                            .model(sentimentModel)
                            .maxTokens(256)
                            .build())
                    .build();

            chatResponse = chatClient.prompt(
                    new Prompt(List.of(new UserMessage(promptText))))
                    .call()
                    .chatResponse();

        } catch (Exception ex) {
            log.error("Anthropic API call failed for sentiment analysis: tenantId={}, ticketId={}",
                    tenantId, ticketId, ex);
            saveInteraction(tenantId, ticketId, cleanText, "API_ERROR: " + ex.getMessage(),
                    sentimentModel, 0, 0, System.currentTimeMillis() - startMs);
            return FALLBACK_RESULT;
        }

        long latencyMs = System.currentTimeMillis() - startMs;
        String rawResponse = chatResponse.getResult().getOutput().getText();

        int inputTokens = 0;
        int outputTokens = 0;
        if (chatResponse.getMetadata() != null && chatResponse.getMetadata().getUsage() != null) {
            inputTokens = (int) chatResponse.getMetadata().getUsage().getPromptTokens();
            outputTokens = (int) chatResponse.getMetadata().getUsage().getGenerationTokens();
        }

        saveInteraction(tenantId, ticketId, cleanText, rawResponse,
                sentimentModel, inputTokens, outputTokens, latencyMs);

        try {
            // Strip potential markdown code fences before parsing
            String json = rawResponse.trim();
            if (json.startsWith("```")) {
                json = json.replaceAll("^```[a-z]*\\n?", "").replaceAll("```$", "").trim();
            }
            JsonNode node = objectMapper.readTree(json);
            String label = node.path("label").asText("neutral");
            double score = node.path("score").asDouble(0.0);
            String reason = node.path("reason").asText("no reason provided");

            log.info("Sentiment analysis completed: tenantId={}, ticketId={}, label={}, score={}",
                    tenantId, ticketId, label, score);

            return new SentimentResult(label, score, reason);

        } catch (Exception ex) {
            log.warn("Failed to parse sentiment JSON response: tenantId={}, ticketId={}",
                    tenantId, ticketId, ex);
            return FALLBACK_RESULT;
        }
    }

    private void saveInteraction(String tenantId, String ticketId, String prompt, String response,
            String model, int inputTokens, int outputTokens, long latencyMs) {
        try {
            AiInteraction interaction = new AiInteraction();
            interaction.setTenantId(tenantId);
            interaction.setTicketId(ticketId);
            interaction.setInteractionType(InteractionType.SENTIMENT);
            interaction.setPrompt(prompt);
            interaction.setResponse(response);
            interaction.setModel(model);
            interaction.setInputTokens(inputTokens);
            interaction.setOutputTokens(outputTokens);
            interaction.setLatencyMs(latencyMs);
            interaction.setCreatedAt(Instant.now());
            aiInteractionRepository.save(interaction);
        } catch (Exception ex) {
            log.error("Failed to save AI interaction to MongoDB: tenantId={}, ticketId={}",
                    tenantId, ticketId, ex);
        }
    }
}
