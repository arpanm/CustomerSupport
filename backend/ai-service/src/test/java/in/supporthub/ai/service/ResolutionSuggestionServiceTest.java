package in.supporthub.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.supporthub.ai.domain.AiInteraction;
import in.supporthub.ai.dto.ResolutionSuggestion;
import in.supporthub.ai.repository.AiInteractionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ResolutionSuggestionService}.
 *
 * <p>Tests cover:
 * <ul>
 *   <li>REQ-AI-RES-01: LLM is called when suggestions are requested</li>
 *   <li>REQ-AI-RES-04: Result contains at most 3 suggestions</li>
 *   <li>REQ-AI-RES-08: Correct model identifier is used (claude-sonnet)</li>
 *   <li>REQ-SEC-13: PII is stripped from ticket text before the LLM prompt is sent or persisted</li>
 *   <li>REQ-AI-RES-01 (degradation): Empty list returned when LLM call fails</li>
 *   <li>REQ-AI-SENT-03: Empty list returned when primary model is unavailable (model-level failure)</li>
 * </ul>
 *
 * <p>The {@link AnthropicChatModel} is mocked to prevent real API calls.
 * {@link ChatClient} delegates to the underlying model via
 * {@code anthropicChatModel.call(Prompt)}, which is the interception point used here.
 */
@ExtendWith(MockitoExtension.class)
class ResolutionSuggestionServiceTest {

    // ---------------------------------------------------------------------------
    // Valid JSON payload that the mocked model returns for success-path tests
    // ---------------------------------------------------------------------------
    private static final String THREE_SUGGESTIONS_JSON = """
            [
              {"title": "Order Delayed — Apology", "content": "We apologise for the delay...", "confidence": 0.92},
              {"title": "Refund Initiated",         "content": "Your refund has been raised...", "confidence": 0.85},
              {"title": "Escalation Template",      "content": "We are escalating your issue...", "confidence": 0.78}
            ]
            """;

    private static final String ONE_SUGGESTION_JSON = """
            [
              {"title": "Order Delayed — Apology", "content": "We apologise for the delay...", "confidence": 0.92}
            ]
            """;

    private static final String DEFAULT_TENANT = "tenant-abc";
    private static final String DEFAULT_TICKET = "ticket-xyz";

    // ---------------------------------------------------------------------------
    // Mocks
    // ---------------------------------------------------------------------------

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

    // ---------------------------------------------------------------------------
    // System under test
    // ---------------------------------------------------------------------------

    private ResolutionSuggestionService service;

    @BeforeEach
    void setUp() {
        PiiStripper piiStripper = new PiiStripper();
        ObjectMapper objectMapper = new ObjectMapper();
        service = new ResolutionSuggestionService(
                anthropicChatModel, aiInteractionRepository, piiStripper, objectMapper);
        // Inject the model name that matches the production default
        ReflectionTestUtils.setField(service, "resolutionModel", "claude-sonnet-4-5");
    }

    // ---------------------------------------------------------------------------
    // Helper — wire the mock chain for a successful API call
    // ---------------------------------------------------------------------------

    private void givenModelReturns(String json) {
        AssistantMessage assistantMessage = new AssistantMessage(json);
        when(generation.getOutput()).thenReturn(assistantMessage);
        when(chatResponse.getResult()).thenReturn(generation);
        when(chatResponse.getMetadata()).thenReturn(chatResponseMetadata);
        when(chatResponseMetadata.getUsage()).thenReturn(usage);
        when(usage.getPromptTokens()).thenReturn(80);
        when(usage.getCompletionTokens()).thenReturn(200);
        when(anthropicChatModel.call(any(Prompt.class))).thenReturn(chatResponse);
    }

    // ===========================================================================
    // REQ-AI-RES-01: Suggestions are generated when triggered (LLM is called)
    // ===========================================================================

    @Test
    @DisplayName("REQ-AI-RES-01: LLM call is made when getSuggestions is invoked")
    void shouldInvokeLlmWhenGetSuggestionsIsCalled() {
        givenModelReturns(THREE_SUGGESTIONS_JSON);

        service.getSuggestions(DEFAULT_TENANT, DEFAULT_TICKET,
                "Order not delivered", "My food has not arrived after 2 hours.", "order-not-delivered");

        verify(anthropicChatModel).call(any(Prompt.class));
    }

    // ===========================================================================
    // REQ-AI-RES-04: Service returns at most 3 suggestions
    // ===========================================================================

    @Test
    @DisplayName("REQ-AI-RES-04: Result contains exactly 3 suggestions when LLM returns 3")
    void shouldReturnThreeSuggestionsWhenLlmReturnsThree() {
        givenModelReturns(THREE_SUGGESTIONS_JSON);

        List<ResolutionSuggestion> result = service.getSuggestions(
                DEFAULT_TENANT, DEFAULT_TICKET,
                "Order not delivered", "My food has not arrived.", "order-not-delivered");

        assertThat(result).hasSize(3);
    }

    @Test
    @DisplayName("REQ-AI-RES-04: Result has at most 3 suggestions (fewer is also acceptable)")
    void shouldReturnFewerThanThreeSuggestionsWhenLlmReturnsFewer() {
        givenModelReturns(ONE_SUGGESTION_JSON);

        List<ResolutionSuggestion> result = service.getSuggestions(
                DEFAULT_TENANT, DEFAULT_TICKET,
                "Billing issue", "I was charged twice.", "billing-dispute");

        assertThat(result).hasSizeLessThanOrEqualTo(3);
        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("REQ-AI-RES-04: Suggestions contain expected title and confidence fields")
    void shouldReturnSuggestionsWithExpectedFields() {
        givenModelReturns(THREE_SUGGESTIONS_JSON);

        List<ResolutionSuggestion> result = service.getSuggestions(
                DEFAULT_TENANT, DEFAULT_TICKET,
                "Order not delivered", "My food has not arrived.", "order-not-delivered");

        assertThat(result).allSatisfy(suggestion -> {
            assertThat(suggestion.title()).isNotBlank();
            assertThat(suggestion.content()).isNotBlank();
            assertThat(suggestion.confidence()).isBetween(0.0, 1.0);
        });
    }

    // ===========================================================================
    // REQ-AI-RES-08: Service uses the correct model (claude-sonnet)
    // ===========================================================================

    @Test
    @DisplayName("REQ-AI-RES-08: Saved interaction record stores the claude-sonnet model identifier")
    void shouldPersistCorrectModelIdentifierInInteractionRecord() {
        givenModelReturns(THREE_SUGGESTIONS_JSON);

        service.getSuggestions(DEFAULT_TENANT, DEFAULT_TICKET,
                "Order not delivered", "My food has not arrived.", "order-not-delivered");

        ArgumentCaptor<AiInteraction> captor = ArgumentCaptor.forClass(AiInteraction.class);
        verify(aiInteractionRepository).save(captor.capture());

        AiInteraction saved = captor.getValue();
        // The model field must reference a Claude Sonnet variant, not a cheaper/different model
        assertThat(saved.getModel())
                .as("Resolution suggestions must use a Sonnet-class model, not Haiku or another model")
                .containsIgnoringCase("sonnet");
    }

    // ===========================================================================
    // REQ-SEC-13: PII is stripped before the LLM prompt is constructed/persisted
    // ===========================================================================

    @Test
    @DisplayName("REQ-SEC-13: Phone number in description is not present in persisted prompt")
    void shouldStripPhoneNumberFromDescriptionBeforeLlmCall() {
        givenModelReturns(THREE_SUGGESTIONS_JSON);

        service.getSuggestions(DEFAULT_TENANT, DEFAULT_TICKET,
                "Delivery issue",
                "Call me at 9876543210 to discuss my order.",
                "order-not-delivered");

        ArgumentCaptor<AiInteraction> captor = ArgumentCaptor.forClass(AiInteraction.class);
        verify(aiInteractionRepository).save(captor.capture());

        AiInteraction saved = captor.getValue();
        assertThat(saved.getPrompt())
                .as("Raw phone number must not appear in the persisted prompt")
                .doesNotContain("9876543210");
        assertThat(saved.getPrompt())
                .as("Phone number placeholder must be present in the persisted prompt")
                .contains("[PHONE]");
    }

    @Test
    @DisplayName("REQ-SEC-13: Email address in description is not present in persisted prompt")
    void shouldStripEmailAddressFromDescriptionBeforeLlmCall() {
        givenModelReturns(THREE_SUGGESTIONS_JSON);

        service.getSuggestions(DEFAULT_TENANT, DEFAULT_TICKET,
                "Refund request",
                "Please send the refund confirmation to customer@example.com",
                "refund-request");

        ArgumentCaptor<AiInteraction> captor = ArgumentCaptor.forClass(AiInteraction.class);
        verify(aiInteractionRepository).save(captor.capture());

        AiInteraction saved = captor.getValue();
        assertThat(saved.getPrompt())
                .as("Raw email must not appear in the persisted prompt")
                .doesNotContain("customer@example.com");
        assertThat(saved.getPrompt())
                .as("Email placeholder must be present in the persisted prompt")
                .contains("[EMAIL]");
    }

    @Test
    @DisplayName("REQ-SEC-13: Both phone and email in description are stripped before LLM call")
    void shouldStripBothPhoneAndEmailFromDescription() {
        givenModelReturns(THREE_SUGGESTIONS_JSON);

        service.getSuggestions(DEFAULT_TENANT, DEFAULT_TICKET,
                "Contact update",
                "Reach me at 9876543210 or user@food.in — I need help.",
                "general-inquiry");

        ArgumentCaptor<AiInteraction> captor = ArgumentCaptor.forClass(AiInteraction.class);
        verify(aiInteractionRepository).save(captor.capture());

        AiInteraction saved = captor.getValue();
        assertThat(saved.getPrompt()).doesNotContain("9876543210");
        assertThat(saved.getPrompt()).doesNotContain("user@food.in");
        assertThat(saved.getPrompt()).contains("[PHONE]");
        assertThat(saved.getPrompt()).contains("[EMAIL]");
    }

    // ===========================================================================
    // REQ-AI-RES-01: Graceful degradation — empty list returned when LLM fails
    // ===========================================================================

    @Test
    @DisplayName("REQ-AI-RES-01: Empty list is returned (not exception) when LLM call throws")
    void shouldReturnEmptyListWhenLlmCallThrowsRuntimeException() {
        when(anthropicChatModel.call(any(Prompt.class)))
                .thenThrow(new RuntimeException("Anthropic API is unreachable"));

        List<ResolutionSuggestion> result = service.getSuggestions(
                DEFAULT_TENANT, DEFAULT_TICKET,
                "Order not delivered", "My food has not arrived.", "order-not-delivered");

        assertThat(result)
                .as("Service must return an empty list, not propagate the exception")
                .isNotNull()
                .isEmpty();
    }

    @Test
    @DisplayName("REQ-AI-RES-01: getSuggestions never throws even when LLM is unavailable")
    void shouldNeverThrowWhenLlmFails() {
        when(anthropicChatModel.call(any(Prompt.class)))
                .thenThrow(new RuntimeException("Network timeout"));

        assertThatCode(() -> service.getSuggestions(
                DEFAULT_TENANT, DEFAULT_TICKET,
                "Order not delivered", "My food has not arrived.", "order-not-delivered"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("REQ-AI-RES-01: Empty list is returned when LLM responds with unparseable JSON")
    void shouldReturnEmptyListWhenLlmResponseIsNotValidJson() {
        // Wire the mock chain so the model returns non-JSON text
        AssistantMessage assistantMessage = new AssistantMessage(
                "Sorry, I cannot generate suggestions for this ticket right now.");
        when(generation.getOutput()).thenReturn(assistantMessage);
        when(chatResponse.getResult()).thenReturn(generation);
        when(chatResponse.getMetadata()).thenReturn(chatResponseMetadata);
        when(chatResponseMetadata.getUsage()).thenReturn(usage);
        when(usage.getPromptTokens()).thenReturn(80);
        when(usage.getCompletionTokens()).thenReturn(20);
        when(anthropicChatModel.call(any(Prompt.class))).thenReturn(chatResponse);

        List<ResolutionSuggestion> result = service.getSuggestions(
                DEFAULT_TENANT, DEFAULT_TICKET,
                "Order not delivered", "My food has not arrived.", "order-not-delivered");

        assertThat(result)
                .as("Malformed LLM response must result in an empty list, not an exception")
                .isNotNull()
                .isEmpty();
    }

    @Test
    @DisplayName("REQ-AI-RES-01: Markdown-fenced JSON is parsed correctly (not treated as error)")
    void shouldParseMarkdownFencedJsonResponse() {
        String fencedJson = "```json\n" + THREE_SUGGESTIONS_JSON + "\n```";
        AssistantMessage assistantMessage = new AssistantMessage(fencedJson);
        when(generation.getOutput()).thenReturn(assistantMessage);
        when(chatResponse.getResult()).thenReturn(generation);
        when(chatResponse.getMetadata()).thenReturn(chatResponseMetadata);
        when(chatResponseMetadata.getUsage()).thenReturn(usage);
        when(usage.getPromptTokens()).thenReturn(80);
        when(usage.getCompletionTokens()).thenReturn(200);
        when(anthropicChatModel.call(any(Prompt.class))).thenReturn(chatResponse);

        List<ResolutionSuggestion> result = service.getSuggestions(
                DEFAULT_TENANT, DEFAULT_TICKET,
                "Order not delivered", "My food has not arrived.", "order-not-delivered");

        assertThat(result).hasSize(3);
    }

    // ===========================================================================
    // REQ-AI-SENT-03: Fallback when primary model is unavailable
    // (For ResolutionSuggestionService the fallback strategy is identical to the
    //  API-failure path: return an empty list and never propagate the error.)
    // ===========================================================================

    @Test
    @DisplayName("REQ-AI-SENT-03: Empty list is returned when primary model throws a service-unavailable error")
    void shouldReturnEmptyListWhenPrimaryModelIsUnavailable() {
        when(anthropicChatModel.call(any(Prompt.class)))
                .thenThrow(new RuntimeException("503 Service Unavailable from Anthropic"));

        List<ResolutionSuggestion> result = service.getSuggestions(
                DEFAULT_TENANT, DEFAULT_TICKET,
                "Billing issue", "I was charged twice for order ORD-001.", "billing-dispute");

        assertThat(result)
                .as("Service must degrade gracefully to empty list when the primary model is unavailable")
                .isNotNull()
                .isEmpty();
    }

    @Test
    @DisplayName("REQ-AI-SENT-03: Failure interaction is persisted with error detail when model is unavailable")
    void shouldPersistErrorInteractionWhenPrimaryModelIsUnavailable() {
        when(anthropicChatModel.call(any(Prompt.class)))
                .thenThrow(new RuntimeException("503 Service Unavailable from Anthropic"));

        service.getSuggestions(DEFAULT_TENANT, DEFAULT_TICKET,
                "Billing issue", "I was charged twice for order ORD-001.", "billing-dispute");

        ArgumentCaptor<AiInteraction> captor = ArgumentCaptor.forClass(AiInteraction.class);
        verify(aiInteractionRepository).save(captor.capture());

        AiInteraction saved = captor.getValue();
        assertThat(saved.getTenantId()).isEqualTo(DEFAULT_TENANT);
        assertThat(saved.getTicketId()).isEqualTo(DEFAULT_TICKET);
        assertThat(saved.getInteractionType())
                .isEqualTo(AiInteraction.InteractionType.RESOLUTION_SUGGESTION);
        assertThat(saved.getResponse())
                .as("Error response must record that an API error occurred")
                .startsWith("API_ERROR:");
    }

    // ===========================================================================
    // Audit / interaction persistence tests
    // ===========================================================================

    @Test
    @DisplayName("Successful call persists an interaction record with correct tenant and ticket IDs")
    void shouldPersistInteractionRecordOnSuccess() {
        givenModelReturns(THREE_SUGGESTIONS_JSON);

        service.getSuggestions("tenant-999", "ticket-111",
                "Wrong item delivered", "I received a pizza instead of biryani.", "wrong-item");

        ArgumentCaptor<AiInteraction> captor = ArgumentCaptor.forClass(AiInteraction.class);
        verify(aiInteractionRepository).save(captor.capture());

        AiInteraction saved = captor.getValue();
        assertThat(saved.getTenantId()).isEqualTo("tenant-999");
        assertThat(saved.getTicketId()).isEqualTo("ticket-111");
        assertThat(saved.getInteractionType())
                .isEqualTo(AiInteraction.InteractionType.RESOLUTION_SUGGESTION);
        assertThat(saved.getInputTokens()).isEqualTo(80);
        assertThat(saved.getOutputTokens()).isEqualTo(200);
        assertThat(saved.getLatencyMs()).isGreaterThanOrEqualTo(0);
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("User message includes category slug, title, and (PII-stripped) description")
    void shouldIncludeCategoryTitleAndDescriptionInPrompt() {
        givenModelReturns(THREE_SUGGESTIONS_JSON);

        service.getSuggestions(DEFAULT_TENANT, DEFAULT_TICKET,
                "Item missing from order",
                "Half the items in my order are missing.",
                "item-missing");

        ArgumentCaptor<AiInteraction> captor = ArgumentCaptor.forClass(AiInteraction.class);
        verify(aiInteractionRepository).save(captor.capture());

        AiInteraction saved = captor.getValue();
        assertThat(saved.getPrompt()).contains("item-missing");
        assertThat(saved.getPrompt()).contains("Item missing from order");
        assertThat(saved.getPrompt()).contains("Half the items in my order are missing.");
    }

    // ===========================================================================
    // REQ-SEC-13: Name redaction — documented as NOT yet implemented
    // ===========================================================================

    @Test
    @Disabled("REQ-SEC-13: Name/person entity redaction is not yet implemented in PiiStripper. "
            + "PiiStripper currently handles only phone numbers and email addresses via regex. "
            + "Name detection requires NER (Named Entity Recognition) and is tracked as a future "
            + "enhancement. Enable this test when NER-based PII stripping is implemented.")
    @DisplayName("REQ-SEC-13 (NOT IMPLEMENTED): Person names are redacted before LLM call")
    void shouldStripPersonNamesFromDescriptionBeforeLlmCall() {
        givenModelReturns(THREE_SUGGESTIONS_JSON);

        service.getSuggestions(DEFAULT_TENANT, DEFAULT_TICKET,
                "Refund request",
                "My name is Rahul Sharma and I want a refund for order ORD-002.",
                "refund-request");

        ArgumentCaptor<AiInteraction> captor = ArgumentCaptor.forClass(AiInteraction.class);
        verify(aiInteractionRepository).save(captor.capture());

        AiInteraction saved = captor.getValue();
        assertThat(saved.getPrompt()).doesNotContain("Rahul Sharma");
        assertThat(saved.getPrompt()).contains("[NAME]");
    }
}
