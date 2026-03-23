package in.supporthub.faq.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.supporthub.faq.dto.StrapiWebhookPayload;
import in.supporthub.faq.service.StrapiWebhookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Receives and processes Strapi CMS webhook events for FAQ content synchronisation.
 *
 * <p>The endpoint reads the raw request body as a {@code String} to ensure that the
 * HMAC-SHA256 signature is computed over the exact bytes sent by Strapi (before any
 * JSON deserialisation that could alter whitespace or key ordering).
 *
 * <p>Security: the {@code X-Strapi-Signature} header is validated in
 * {@link StrapiWebhookService} before any payload processing begins. An invalid
 * or missing signature results in HTTP 401.
 *
 * <p>Tenant identification: Strapi webhooks must include the {@code X-Tenant-ID} header
 * (configured in the Strapi webhook settings per-tenant). The service will reject requests
 * without a valid tenant ID.
 */
@RestController
@RequestMapping("/api/v1/webhooks/strapi")
@Slf4j
@RequiredArgsConstructor
@Tag(name = "Strapi Webhook", description = "Strapi CMS FAQ content sync webhook")
public class StrapiWebhookController {

    private final StrapiWebhookService strapiWebhookService;
    private final ObjectMapper objectMapper;

    /**
     * Handles an incoming Strapi webhook event.
     *
     * @param rawBody  raw UTF-8 JSON body — required for HMAC validation
     * @param signature value of the {@code X-Strapi-Signature} header
     * @param tenantIdStr tenant UUID from the {@code X-Tenant-ID} header
     * @return HTTP 200 on success, HTTP 401 on signature failure
     */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Receive Strapi CMS webhook for FAQ sync")
    public ResponseEntity<Void> handleWebhook(
            @RequestBody String rawBody,
            @RequestHeader("X-Strapi-Signature") String signature,
            @RequestHeader("X-Tenant-ID") String tenantIdStr) {

        UUID tenantId = UUID.fromString(tenantIdStr);
        log.info("Strapi webhook received: tenantId={}", tenantId);

        StrapiWebhookPayload payload = deserializePayload(rawBody);
        strapiWebhookService.handleWebhook(payload, rawBody, signature, tenantId);
        return ResponseEntity.ok().build();
    }

    private StrapiWebhookPayload deserializePayload(String rawBody) {
        try {
            return objectMapper.readValue(rawBody, StrapiWebhookPayload.class);
        } catch (Exception ex) {
            log.warn("Failed to deserialize Strapi webhook payload: error={}", ex.getMessage());
            throw new IllegalArgumentException("Invalid Strapi webhook payload: " + ex.getMessage(), ex);
        }
    }
}
