package in.supporthub.notification.service.sms;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

/**
 * SMS delivery service backed by the MSG91 Flow API.
 *
 * <p>Sends transactional SMS messages using pre-configured flow templates.
 * The phone number is NEVER logged — only masked flow/reference IDs appear in logs.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class Msg91SmsService {

    private static final String MSG91_API_URL = "https://api.msg91.com/api/v5/flow/";

    private final WebClient webClient;

    @Value("${msg91.api-key:}")
    private String msg91ApiKey;

    @Value("${msg91.flow-id:TICKET_CREATED}")
    private String defaultFlowId;

    /**
     * Sends an SMS for a new ticket creation.
     *
     * <p>Uses VAR1 = ticketNumber, VAR2 = categoryName in the MSG91 flow template.
     * The {@code phoneE164} parameter is used in the API call but NEVER logged.
     *
     * @param phoneE164    Decrypted phone number in E.164 format without the country code prefix
     *                     (e.g., "9876543210" — the service prepends "91").
     * @param ticketNumber Human-readable ticket number (e.g., "FC-2024-001234").
     * @param categoryName Name of the ticket category (e.g., "Order Issue").
     * @return {@code true} if the API call succeeded; {@code false} on any error.
     */
    public boolean sendTicketCreatedSms(String phoneE164, String ticketNumber, String categoryName) {
        if (msg91ApiKey == null || msg91ApiKey.isBlank()) {
            log.warn("MSG91 API key not configured — skipping SMS for ticketNumber={}", ticketNumber);
            return false;
        }

        Map<String, String> requestBody = Map.of(
                "flow_id", defaultFlowId,
                "mobiles", "91" + phoneE164,
                "VAR1", ticketNumber,
                "VAR2", categoryName != null ? categoryName : "Support"
        );

        try {
            webClient.post()
                    .uri(MSG91_API_URL)
                    .header("authkey", msg91ApiKey)
                    .bodyValue(requestBody)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, response ->
                            response.bodyToMono(String.class)
                                    .doOnNext(body -> log.error(
                                            "MSG91 SMS API error status={} ticketNumber={}",
                                            response.statusCode().value(), ticketNumber))
                                    .thenReturn(new RuntimeException(
                                            "MSG91 API error status=" + response.statusCode().value())))
                    .toBodilessEntity()
                    .block();

            log.info("SMS sent via MSG91 ticketNumber={}", ticketNumber);
            return true;

        } catch (Exception e) {
            // Log error without any PII (no phone number)
            log.error("Failed to send SMS via MSG91 ticketNumber={} error={}", ticketNumber, e.getMessage());
            return false;
        }
    }

    /**
     * Sends an SMS for a ticket status change.
     *
     * @param phoneE164    Decrypted phone number — NEVER logged.
     * @param ticketNumber Human-readable ticket number.
     * @param newStatus    New status label (e.g., "RESOLVED").
     * @return {@code true} if successful; {@code false} on error.
     */
    public boolean sendStatusChangedSms(String phoneE164, String ticketNumber, String newStatus) {
        if (msg91ApiKey == null || msg91ApiKey.isBlank()) {
            log.warn("MSG91 API key not configured — skipping status SMS for ticketNumber={}", ticketNumber);
            return false;
        }

        String statusFlowId = resolveStatusFlowId();

        Map<String, String> requestBody = Map.of(
                "flow_id", statusFlowId,
                "mobiles", "91" + phoneE164,
                "VAR1", ticketNumber,
                "VAR2", newStatus
        );

        try {
            webClient.post()
                    .uri(MSG91_API_URL)
                    .header("authkey", msg91ApiKey)
                    .bodyValue(requestBody)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, response ->
                            response.bodyToMono(String.class)
                                    .doOnNext(body -> log.error(
                                            "MSG91 status SMS API error status={} ticketNumber={}",
                                            response.statusCode().value(), ticketNumber))
                                    .thenReturn(new RuntimeException(
                                            "MSG91 API error status=" + response.statusCode().value())))
                    .toBodilessEntity()
                    .block();

            log.info("Status SMS sent via MSG91 ticketNumber={} newStatus={}", ticketNumber, newStatus);
            return true;

        } catch (Exception e) {
            log.error("Failed to send status SMS via MSG91 ticketNumber={} newStatus={} error={}",
                    ticketNumber, newStatus, e.getMessage());
            return false;
        }
    }

    private String resolveStatusFlowId() {
        // In production this could be configured per-event-type; defaulting to the base flow.
        return defaultFlowId;
    }
}
