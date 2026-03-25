package in.supporthub.ai.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.supporthub.ai.domain.AiInteraction;
import in.supporthub.ai.domain.AiInteraction.InteractionType;
import in.supporthub.ai.dto.ResolutionSuggestion;
import in.supporthub.ai.repository.AiInteractionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

/**
 * Generates resolution suggestions for support agents using Claude Sonnet.
 *
 * <p>Suggestions are based on the ticket category, title, and description (PII-stripped).
 * On any failure, an empty list is returned — this service never throws to its callers.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ResolutionSuggestionService {

    private static final String SYSTEM_PROMPT =
            "You are a customer support assistant for an Indian food delivery platform. "
            + "Suggest 3 resolution templates for the agent. "
            + "Respond with JSON array: "
            + "[{\"title\": \"...\", \"content\": \"...\", \"confidence\": 0.0-1.0}]";

    private final AnthropicChatModel anthropicChatModel;
    private final AiInteractionRepository aiInteractionRepository;
    private final PiiStripper piiStripper;
    private final ObjectMapper objectMapper;

    @Value("${anthropic.resolution-model:claude-sonnet-4-5}")
    private String resolutionModel;

    /**
     * Returns up to 3 resolution suggestion templates for a support agent.
     *
     * @param tenantId     the owning tenant UUID
     * @param ticketId     the ticket UUID
     * @param title        the ticket title
     * @param description  the ticket description (PII will be stripped)
     * @param categorySlug the ticket category slug (e.g., "order-not-delivered")
     * @return list of {@link ResolutionSuggestion} — empty list on failure, never null
     */
    public List<ResolutionSuggestion> getSuggestions(
            String tenantId,
            String ticketId,
            String title,
            String description,
            String categorySlug) {

        String cleanDescription = piiStripper.strip(description);
        String userMessage = "Category: " + categorySlug
                + "\nTitle: " + title
                + "\nDescription: " + cleanDescription;

        long startMs = System.currentTimeMillis();
        ChatResponse chatResponse = null;

        try {
            ChatClient chatClient = ChatClient.builder(anthropicChatModel)
                    .defaultOptions(AnthropicChatOptions.builder()
                            .model(resolutionModel)
                            .maxTokens(1024)
                            .build())
                    .build();

            chatResponse = chatClient.prompt(
                    new Prompt(List.of(
                            new SystemMessage(SYSTEM_PROMPT),
                            new UserMessage(userMessage))))
                    .call()
                    .chatResponse();

        } catch (Exception ex) {
            log.error("Anthropic API call failed for resolution suggestions: tenantId={}, ticketId={}",
                    tenantId, ticketId, ex);
            saveInteraction(tenantId, ticketId, userMessage, "API_ERROR: " + ex.getMessage(),
                    resolutionModel, 0, 0, System.currentTimeMillis() - startMs);
            return Collections.emptyList();
        }

        long latencyMs = System.currentTimeMillis() - startMs;
        String rawResponse = chatResponse.getResult().getOutput().getText();

        int inputTokens = 0;
        int outputTokens = 0;
        if (chatResponse.getMetadata() != null && chatResponse.getMetadata().getUsage() != null) {
            inputTokens = (int) chatResponse.getMetadata().getUsage().getPromptTokens();
            outputTokens = (int) chatResponse.getMetadata().getUsage().getCompletionTokens();
        }

        saveInteraction(tenantId, ticketId, userMessage, rawResponse,
                resolutionModel, inputTokens, outputTokens, latencyMs);

        try {
            // Strip potential markdown code fences before parsing
            String json = rawResponse.trim();
            if (json.startsWith("```")) {
                json = json.replaceAll("^```[a-z]*\\n?", "").replaceAll("```$", "").trim();
            }

            List<ResolutionSuggestion> suggestions = objectMapper.readValue(
                    json, new TypeReference<List<ResolutionSuggestion>>() {});

            log.info("Resolution suggestions generated: tenantId={}, ticketId={}, count={}",
                    tenantId, ticketId, suggestions.size());

            return suggestions;

        } catch (Exception ex) {
            log.warn("Failed to parse resolution suggestions JSON: tenantId={}, ticketId={}",
                    tenantId, ticketId, ex);
            return Collections.emptyList();
        }
    }

    private void saveInteraction(String tenantId, String ticketId, String prompt, String response,
            String model, int inputTokens, int outputTokens, long latencyMs) {
        try {
            AiInteraction interaction = new AiInteraction();
            interaction.setTenantId(tenantId);
            interaction.setTicketId(ticketId);
            interaction.setInteractionType(InteractionType.RESOLUTION_SUGGESTION);
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
