package in.supporthub.ai.controller;

import in.supporthub.ai.domain.AiInteraction;
import in.supporthub.ai.dto.ResolutionSuggestion;
import in.supporthub.ai.dto.ResolutionSuggestionsRequest;
import in.supporthub.ai.dto.SentimentAnalysisRequest;
import in.supporthub.ai.dto.SentimentResult;
import in.supporthub.ai.repository.AiInteractionRepository;
import in.supporthub.ai.service.ResolutionSuggestionService;
import in.supporthub.ai.service.SentimentAnalysisService;
import in.supporthub.shared.security.TenantContextHolder;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST API for AI capabilities consumed by the agent dashboard and internal services.
 *
 * <p>All endpoints require the {@code X-Tenant-ID} header (set by the API gateway).
 * Tenant isolation is enforced via {@link TenantContextHolder}.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>{@code POST /api/v1/ai/sentiment} — on-demand sentiment analysis</li>
 *   <li>{@code POST /api/v1/ai/resolution-suggestions} — resolution templates for agents</li>
 *   <li>{@code GET  /api/v1/ai/interactions/{ticketId}} — AI interaction audit trail</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/ai")
@Slf4j
@RequiredArgsConstructor
public class AiController {

    private final SentimentAnalysisService sentimentAnalysisService;
    private final ResolutionSuggestionService resolutionSuggestionService;
    private final AiInteractionRepository aiInteractionRepository;

    /**
     * Performs on-demand sentiment analysis on the provided text.
     *
     * @param request contains ticketId and raw text to analyse
     * @return sentiment result with label, score, and brief reason
     */
    @PostMapping("/sentiment")
    public ResponseEntity<SentimentResult> analyzeSentiment(
            @Valid @RequestBody SentimentAnalysisRequest request) {

        String tenantId = TenantContextHolder.getTenantId();
        log.info("Sentiment analysis requested: tenantId={}, ticketId={}",
                tenantId, request.ticketId());

        SentimentResult result = sentimentAnalysisService.analyzeSentiment(
                tenantId, request.ticketId(), request.text());

        return ResponseEntity.ok(result);
    }

    /**
     * Returns resolution suggestion templates for the agent.
     *
     * @param request ticket context: ticketId, title, description, categorySlug
     * @return list of resolution suggestions (may be empty on AI failure, never null)
     */
    @PostMapping("/resolution-suggestions")
    public ResponseEntity<List<ResolutionSuggestion>> getSuggestions(
            @Valid @RequestBody ResolutionSuggestionsRequest request) {

        String tenantId = TenantContextHolder.getTenantId();
        log.info("Resolution suggestions requested: tenantId={}, ticketId={}",
                tenantId, request.ticketId());

        List<ResolutionSuggestion> suggestions = resolutionSuggestionService.getSuggestions(
                tenantId,
                request.ticketId(),
                request.title(),
                request.description(),
                request.categorySlug());

        return ResponseEntity.ok(suggestions);
    }

    /**
     * Returns the audit trail of all AI interactions for a ticket.
     *
     * @param ticketId the ticket UUID
     * @return list of AI interactions ordered by creation time
     */
    @GetMapping("/interactions/{ticketId}")
    public ResponseEntity<List<AiInteraction>> getInteractions(
            @PathVariable String ticketId) {

        String tenantId = TenantContextHolder.getTenantId();
        log.info("AI interactions requested: tenantId={}, ticketId={}", tenantId, ticketId);

        List<AiInteraction> interactions =
                aiInteractionRepository.findByTenantIdAndTicketId(tenantId, ticketId);

        return ResponseEntity.ok(interactions);
    }
}
