package in.supporthub.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.supporthub.ai.domain.AiInteraction;
import in.supporthub.ai.dto.SentimentResult;
import in.supporthub.ai.repository.AiInteractionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link SentimentAnalysisService}.
 *
 * <p>Tests cover: valid JSON response, malformed JSON fallback, and API exception fallback.
 * The ChatClient/AnthropicChatModel is mocked to avoid real API calls.
 */
@ExtendWith(MockitoExtension.class)
class SentimentAnalysisServiceTest {

    @Mock
    private AnthropicChatModel anthropicChatModel;

    @Mock
    private AiInteractionRepository aiInteractionRepository;

    @Mock
    private ChatResponse chatResponse;

    @Mock
    private Generation generation;

    @Mock
    private ChatResponseMetadata chatResponseMetadata;

    @Mock
    private Usage usage;

    private PiiStripper piiStripper;
    private ObjectMapper objectMapper;
    private SentimentAnalysisService service;

    @BeforeEach
    void setUp() {
        piiStripper = new PiiStripper();
        objectMapper = new ObjectMapper();
        service = new SentimentAnalysisService(anthropicChatModel, aiInteractionRepository,
                piiStripper, objectMapper);
        ReflectionTestUtils.setField(service, "sentimentModel", "claude-haiku-4-5-20251001");
    }

    @Test
    @DisplayName("Valid sentiment JSON response is parsed correctly")
    void shouldReturnParsedSentimentForValidJson() {
        String validJson = "{\"label\":\"negative\",\"score\":-0.7,\"reason\":\"customer is upset about delay\"}";

        org.springframework.ai.chat.messages.AssistantMessage assistantMessage =
                new org.springframework.ai.chat.messages.AssistantMessage(validJson);
        when(generation.getOutput()).thenReturn(assistantMessage);
        when(chatResponse.getResult()).thenReturn(generation);
        when(chatResponse.getMetadata()).thenReturn(chatResponseMetadata);
        when(chatResponseMetadata.getUsage()).thenReturn(usage);
        when(usage.getPromptTokens()).thenReturn(50L);
        when(usage.getCompletionTokens()).thenReturn(30L);
        when(anthropicChatModel.call(any(org.springframework.ai.chat.prompt.Prompt.class)))
                .thenReturn(chatResponse);

        SentimentResult result = service.analyzeSentiment("tenant-1", "ticket-1",
                "My order has not arrived for 3 days. Very disappointed.");

        assertThat(result.label()).isEqualTo("negative");
        assertThat(result.score()).isEqualTo(-0.7);
        assertThat(result.reason()).isEqualTo("customer is upset about delay");

        ArgumentCaptor<AiInteraction> captor = ArgumentCaptor.forClass(AiInteraction.class);
        verify(aiInteractionRepository).save(captor.capture());
        AiInteraction saved = captor.getValue();
        assertThat(saved.getTenantId()).isEqualTo("tenant-1");
        assertThat(saved.getTicketId()).isEqualTo("ticket-1");
        assertThat(saved.getInteractionType()).isEqualTo(AiInteraction.InteractionType.SENTIMENT);
    }

    @Test
    @DisplayName("Malformed JSON response returns neutral fallback")
    void shouldReturnNeutralFallbackForMalformedJson() {
        String malformedJson = "I cannot determine the sentiment of this message.";

        org.springframework.ai.chat.messages.AssistantMessage assistantMessage =
                new org.springframework.ai.chat.messages.AssistantMessage(malformedJson);
        when(generation.getOutput()).thenReturn(assistantMessage);
        when(chatResponse.getResult()).thenReturn(generation);
        when(chatResponse.getMetadata()).thenReturn(chatResponseMetadata);
        when(chatResponseMetadata.getUsage()).thenReturn(usage);
        when(usage.getPromptTokens()).thenReturn(50L);
        when(usage.getCompletionTokens()).thenReturn(20L);
        when(anthropicChatModel.call(any(org.springframework.ai.chat.prompt.Prompt.class)))
                .thenReturn(chatResponse);

        SentimentResult result = service.analyzeSentiment("tenant-1", "ticket-2",
                "Some ticket text");

        assertThat(result.label()).isEqualTo("neutral");
        assertThat(result.score()).isEqualTo(0.0);
        assertThat(result.reason()).isEqualTo("analysis failed");
    }

    @Test
    @DisplayName("Anthropic API exception returns neutral fallback without rethrowing")
    void shouldReturnNeutralFallbackOnApiException() {
        when(anthropicChatModel.call(any(org.springframework.ai.chat.prompt.Prompt.class)))
                .thenThrow(new RuntimeException("Anthropic API unavailable"));

        SentimentResult result = service.analyzeSentiment("tenant-1", "ticket-3",
                "My order was damaged during delivery.");

        assertThat(result.label()).isEqualTo("neutral");
        assertThat(result.score()).isEqualTo(0.0);
        assertThat(result.reason()).isEqualTo("analysis failed");
    }

    @Test
    @DisplayName("PII in text is stripped before calling model")
    void shouldStripPiiBeforeCallingModel() {
        String validJson = "{\"label\":\"neutral\",\"score\":0.0,\"reason\":\"general complaint\"}";

        org.springframework.ai.chat.messages.AssistantMessage assistantMessage =
                new org.springframework.ai.chat.messages.AssistantMessage(validJson);
        when(generation.getOutput()).thenReturn(assistantMessage);
        when(chatResponse.getResult()).thenReturn(generation);
        when(chatResponse.getMetadata()).thenReturn(chatResponseMetadata);
        when(chatResponseMetadata.getUsage()).thenReturn(usage);
        when(usage.getPromptTokens()).thenReturn(50L);
        when(usage.getCompletionTokens()).thenReturn(25L);
        when(anthropicChatModel.call(any(org.springframework.ai.chat.prompt.Prompt.class)))
                .thenReturn(chatResponse);

        service.analyzeSentiment("tenant-1", "ticket-4",
                "Call me at 9876543210 or email me at user@example.com");

        ArgumentCaptor<AiInteraction> captor = ArgumentCaptor.forClass(AiInteraction.class);
        verify(aiInteractionRepository).save(captor.capture());
        AiInteraction saved = captor.getValue();
        assertThat(saved.getPrompt()).doesNotContain("9876543210");
        assertThat(saved.getPrompt()).doesNotContain("user@example.com");
        assertThat(saved.getPrompt()).contains("[PHONE]");
        assertThat(saved.getPrompt()).contains("[EMAIL]");
    }
}
