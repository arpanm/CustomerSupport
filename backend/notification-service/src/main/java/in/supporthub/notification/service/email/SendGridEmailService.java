package in.supporthub.notification.service.email;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

/**
 * Email delivery service backed by the SendGrid v3 Mail API.
 *
 * <p>Email addresses are NEVER logged — only ticket-scoped identifiers appear in logs.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SendGridEmailService {

    private static final String SENDGRID_API_URL = "https://api.sendgrid.com/v3/mail/send";

    private final WebClient webClient;

    @Value("${sendgrid.api-key:}")
    private String sendGridApiKey;

    @Value("${sendgrid.from-email:support@supporthub.in}")
    private String fromEmail;

    /**
     * Sends a plain-text email via the SendGrid API.
     *
     * <p>The {@code toEmail} parameter is used in the API call body but NEVER logged.
     *
     * @param toEmail      Recipient email address — NEVER log this value.
     * @param subject      Email subject line.
     * @param body         Plain-text email body.
     * @param referenceId  Reference identifier for logging (e.g., ticketNumber).
     * @return {@code true} if the API call succeeded; {@code false} on any error.
     */
    public boolean sendEmail(String toEmail, String subject, String body, String referenceId) {
        if (sendGridApiKey == null || sendGridApiKey.isBlank()) {
            log.warn("SendGrid API key not configured — skipping email for referenceId={}", referenceId);
            return false;
        }

        // Build SendGrid v3 request payload — toEmail is used but NOT logged
        Map<String, Object> requestBody = buildSendGridPayload(toEmail, subject, body);

        try {
            webClient.post()
                    .uri(SENDGRID_API_URL)
                    .header("Authorization", "Bearer " + sendGridApiKey)
                    .bodyValue(requestBody)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, response ->
                            response.bodyToMono(String.class)
                                    .doOnNext(errorBody -> log.error(
                                            "SendGrid API error status={} referenceId={}",
                                            response.statusCode().value(), referenceId))
                                    .then())
                    .toBodilessEntity()
                    .block();

            log.info("Email sent via SendGrid referenceId={}", referenceId);
            return true;

        } catch (Exception e) {
            // Log error without the email address
            log.error("Failed to send email via SendGrid referenceId={} error={}", referenceId, e.getMessage());
            return false;
        }
    }

    private Map<String, Object> buildSendGridPayload(String toEmail, String subject, String body) {
        return Map.of(
                "personalizations", List.of(
                        Map.of("to", List.of(Map.of("email", toEmail)))
                ),
                "from", Map.of("email", fromEmail),
                "subject", subject,
                "content", List.of(
                        Map.of("type", "text/plain", "value", body)
                )
        );
    }
}
