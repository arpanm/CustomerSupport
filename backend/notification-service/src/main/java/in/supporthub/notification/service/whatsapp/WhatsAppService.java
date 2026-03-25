package in.supporthub.notification.service.whatsapp;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

/**
 * WhatsApp delivery service backed by the Meta Business Graph API.
 *
 * <p>This service is optional and degrades gracefully — any failure results in
 * a warning log and a {@code false} return value, never an exception.
 * Phone numbers are NEVER logged.
 *
 * <p>Uses WhatsApp template messages for transactional notifications.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class WhatsAppService {

    private static final String META_GRAPH_API_BASE = "https://graph.facebook.com/v19.0";

    private final WebClient webClient;

    @Value("${whatsapp.access-token:}")
    private String accessToken;

    @Value("${whatsapp.phone-number-id:}")
    private String phoneNumberId;

    /**
     * Sends a WhatsApp template message for a newly created ticket.
     *
     * <p>Uses the {@code ticket_created} template with:
     * <ul>
     *   <li>Component 1 param: ticketNumber</li>
     *   <li>Component 2 param: categoryName</li>
     * </ul>
     * The {@code phoneE164} parameter is used in the API call but NEVER logged.
     *
     * @param phoneE164    Decrypted phone number — NEVER log this value.
     * @param ticketNumber Human-readable ticket number (e.g., "FC-2024-001234").
     * @param categoryName Name of the ticket category.
     * @return {@code true} if the API call succeeded; {@code false} on any error (graceful degradation).
     */
    public boolean sendTicketCreatedMessage(String phoneE164, String ticketNumber, String categoryName) {
        if (!isConfigured()) {
            log.warn("WhatsApp not configured — skipping message for ticketNumber={}", ticketNumber);
            return false;
        }

        Map<String, Object> payload = buildTemplatePayload(
                phoneE164,
                "ticket_created",
                List.of(
                        buildTextParam(ticketNumber),
                        buildTextParam(categoryName != null ? categoryName : "Support")
                )
        );

        return executeApiCall(payload, ticketNumber);
    }

    /**
     * Sends a WhatsApp template message for a ticket status change.
     *
     * @param phoneE164    Decrypted phone number — NEVER log this value.
     * @param ticketNumber Human-readable ticket number.
     * @param newStatus    New status label (e.g., "RESOLVED").
     * @return {@code true} if successful; {@code false} on any error.
     */
    public boolean sendStatusChangedMessage(String phoneE164, String ticketNumber, String newStatus) {
        if (!isConfigured()) {
            log.warn("WhatsApp not configured — skipping status message for ticketNumber={}", ticketNumber);
            return false;
        }

        Map<String, Object> payload = buildTemplatePayload(
                phoneE164,
                "ticket_status_changed",
                List.of(
                        buildTextParam(ticketNumber),
                        buildTextParam(newStatus)
                )
        );

        return executeApiCall(payload, ticketNumber);
    }

    private boolean executeApiCall(Map<String, Object> payload, String ticketNumber) {
        try {
            String apiUrl = META_GRAPH_API_BASE + "/" + phoneNumberId + "/messages";

            webClient.post()
                    .uri(apiUrl)
                    .header("Authorization", "Bearer " + accessToken)
                    .bodyValue(payload)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, response ->
                            response.bodyToMono(String.class)
                                    .doOnNext(body -> log.warn(
                                            "WhatsApp API returned error status={} ticketNumber={}",
                                            response.statusCode().value(), ticketNumber))
                                    .thenReturn(new RuntimeException(
                                            "WhatsApp API error status=" + response.statusCode().value())))
                    .toBodilessEntity()
                    .block();

            log.info("WhatsApp message sent ticketNumber={}", ticketNumber);
            return true;

        } catch (Exception e) {
            // Graceful degradation — warn and return false, never rethrow
            log.warn("WhatsApp message failed (graceful degradation) ticketNumber={} error={}",
                    ticketNumber, e.getMessage());
            return false;
        }
    }

    private Map<String, Object> buildTemplatePayload(
            String phoneE164, String templateName, List<Map<String, Object>> params) {
        return Map.of(
                "messaging_product", "whatsapp",
                "to", "91" + phoneE164,
                "type", "template",
                "template", Map.of(
                        "name", templateName,
                        "language", Map.of("code", "en_US"),
                        "components", List.of(
                                Map.of(
                                        "type", "body",
                                        "parameters", params
                                )
                        )
                )
        );
    }

    private Map<String, Object> buildTextParam(String value) {
        return Map.of("type", "text", "text", value);
    }

    private boolean isConfigured() {
        return accessToken != null && !accessToken.isBlank()
                && phoneNumberId != null && !phoneNumberId.isBlank();
    }
}
