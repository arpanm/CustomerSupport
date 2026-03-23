package in.supporthub.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;

import java.util.Map;

/**
 * MSG91 SMS provider implementation.
 *
 * <p>Calls the MSG91 REST API to dispatch OTP SMS messages.
 * Phone numbers and OTP values are NEVER logged by this class.
 *
 * <p>API reference: https://docs.msg91.com/reference/send-otp
 */
@Service
@Slf4j
public class Msg91SmsService implements SmsService {

    private static final String MSG91_OTP_URL = "https://api.msg91.com/api/v5/otp";

    private final RestTemplate restTemplate;
    private final String apiKey;
    private final String senderId;
    private final String templateId;

    public Msg91SmsService(
            RestTemplate restTemplate,
            @Value("${msg91.api-key}") String apiKey,
            @Value("${msg91.sender-id}") String senderId,
            @Value("${msg91.template-id}") String templateId) {
        this.restTemplate = restTemplate;
        this.apiKey = apiKey;
        this.senderId = senderId;
        this.templateId = templateId;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Sends OTP via MSG91 REST API. On failure, logs the error without including
     * the phone number or OTP in the log message.
     */
    @Override
    public void sendOtp(String phone, String otp, String tenantId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("authkey", apiKey);

        Map<String, String> body = Map.of(
                "mobile", phone,
                "otp", otp,
                "sender", senderId,
                "template_id", templateId
        );

        try {
            restTemplate.exchange(
                    MSG91_OTP_URL,
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    String.class
            );
            log.info("OTP SMS dispatched via MSG91: tenantId={}", tenantId);
        } catch (RestClientException ex) {
            log.error("Failed to send OTP SMS via MSG91: tenantId={}, error={}", tenantId, ex.getMessage(), ex);
            throw new RuntimeException("SMS delivery failed. Please try again.", ex);
        }
    }
}
