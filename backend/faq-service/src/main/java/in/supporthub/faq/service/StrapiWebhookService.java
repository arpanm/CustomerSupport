package in.supporthub.faq.service;

import in.supporthub.faq.domain.FaqEntry;
import in.supporthub.faq.dto.StrapiEntry;
import in.supporthub.faq.dto.StrapiWebhookPayload;
import in.supporthub.faq.exception.WebhookAuthException;
import in.supporthub.faq.repository.FaqRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

/**
 * Processes Strapi CMS webhook events to keep the local {@code faq_entries} table
 * in sync with Strapi content.
 *
 * <p>Every incoming webhook request is authenticated via HMAC-SHA256 of the raw
 * request body using the shared secret configured in {@code strapi.webhook-secret}.
 * An invalid or missing signature results in a {@link WebhookAuthException} (HTTP 401).
 *
 * <p>Supported events:
 * <ul>
 *   <li>{@code entry.create} — create or update local FAQ entry (upsert), keep unpublished</li>
 *   <li>{@code entry.update} — upsert local FAQ entry, regenerate embedding</li>
 *   <li>{@code entry.publish} — upsert + set {@code isPublished=true}</li>
 *   <li>{@code entry.unpublish} — set {@code isPublished=false}</li>
 *   <li>{@code entry.delete} — set {@code isPublished=false} (soft delete, preserve history)</li>
 * </ul>
 *
 * <p>HMAC computation note: the signature must never be logged. Only the boolean result
 * of the comparison is relevant for security auditing.
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class StrapiWebhookService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final FaqRepository faqRepository;
    private final EmbeddingService embeddingService;

    @Value("${strapi.webhook-secret:}")
    private String webhookSecret;

    /**
     * Validates the HMAC-SHA256 signature and dispatches the event to the appropriate handler.
     *
     * @param payload       deserialized Strapi webhook payload
     * @param rawBody       raw UTF-8 request body (used for HMAC verification)
     * @param hmacSignature value from the {@code X-Strapi-Signature} header
     * @param tenantId      tenant UUID from the {@code X-Tenant-ID} header
     * @throws WebhookAuthException if the signature does not match
     */
    public void handleWebhook(StrapiWebhookPayload payload, String rawBody,
                               String hmacSignature, UUID tenantId) {
        validateHmac(rawBody, hmacSignature);

        String event = payload.event();
        log.info("Strapi webhook received: event={}, tenantId={}", event, tenantId);

        if (payload.entry() == null) {
            log.warn("Strapi webhook has no entry payload: event={}, tenantId={}", event, tenantId);
            return;
        }

        switch (event) {
            case "entry.create", "entry.update" -> upsertFaq(payload.entry(), tenantId, false);
            case "entry.publish" -> upsertFaq(payload.entry(), tenantId, true);
            case "entry.unpublish", "entry.delete" -> unpublishFaq(payload.entry(), tenantId);
            default -> log.info("Ignoring unsupported Strapi event: event={}, tenantId={}",
                    event, tenantId);
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Validates the HMAC-SHA256 signature of the raw request body.
     *
     * <p>Uses a constant-time comparison ({@link java.security.MessageDigest#isEqual}) to
     * prevent timing attacks. The received signature is never logged.
     *
     * @throws WebhookAuthException if the secret is not configured or signature is invalid
     */
    private void validateHmac(String rawBody, String receivedSignature) {
        if (webhookSecret == null || webhookSecret.isBlank()) {
            log.error("strapi.webhook-secret is not configured — rejecting all webhook requests");
            throw new WebhookAuthException("Strapi webhook secret is not configured");
        }

        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            SecretKeySpec keySpec = new SecretKeySpec(
                    webhookSecret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
            mac.init(keySpec);
            byte[] computedBytes = mac.doFinal(rawBody.getBytes(StandardCharsets.UTF_8));
            String computedSignature = HexFormat.of().formatHex(computedBytes);

            if (!constantTimeEquals(computedSignature, receivedSignature)) {
                log.warn("Strapi webhook HMAC validation failed");
                throw new WebhookAuthException();
            }
        } catch (NoSuchAlgorithmException | InvalidKeyException ex) {
            log.error("HMAC computation error: {}", ex.getMessage(), ex);
            throw new WebhookAuthException("HMAC computation error: " + ex.getMessage());
        }
    }

    private void upsertFaq(StrapiEntry entry, UUID tenantId, boolean publish) {
        Optional<FaqEntry> existing = faqRepository.findByTenantIdAndStrapiId(tenantId, entry.id());

        FaqEntry faqEntry = existing.orElseGet(() -> {
            log.info("Creating new FAQ from Strapi: strapiId={}, tenantId={}", entry.id(), tenantId);
            return FaqEntry.builder()
                    .tenantId(tenantId)
                    .strapiId(entry.id())
                    .build();
        });

        boolean contentChanged = !entry.question().equals(faqEntry.getQuestion())
                || !entry.answer().equals(faqEntry.getAnswer());

        faqEntry.setQuestion(entry.question());
        faqEntry.setAnswer(entry.answer());
        faqEntry.setTags(entry.tags() != null
                ? entry.tags().toArray(new String[0])
                : new String[0]);
        faqEntry.setPublished(publish || (existing.isPresent() && faqEntry.isPublished()));

        // Regenerate embedding if content changed or embedding is missing
        if (contentChanged || faqEntry.getEmbedding() == null
                || faqEntry.getEmbedding().length == 0) {
            float[] embedding = embeddingService.generateEmbedding(entry.question(), entry.answer());
            if (embedding.length > 0) {
                faqEntry.setEmbedding(embedding);
            }
        }

        faqRepository.save(faqEntry);
        log.info("FAQ upserted from Strapi: strapiId={}, tenantId={}, published={}",
                entry.id(), tenantId, faqEntry.isPublished());
    }

    private void unpublishFaq(StrapiEntry entry, UUID tenantId) {
        faqRepository.findByTenantIdAndStrapiId(tenantId, entry.id()).ifPresentOrElse(
                faqEntry -> {
                    faqEntry.setPublished(false);
                    faqRepository.save(faqEntry);
                    log.info("FAQ unpublished via Strapi webhook: strapiId={}, tenantId={}",
                            entry.id(), tenantId);
                },
                () -> log.warn("Unpublish webhook for unknown FAQ: strapiId={}, tenantId={}",
                        entry.id(), tenantId)
        );
    }

    /**
     * Constant-time string comparison to prevent timing side-channel attacks on
     * HMAC verification.
     */
    private boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        byte[] aBytes = a.getBytes(StandardCharsets.UTF_8);
        byte[] bBytes = b.getBytes(StandardCharsets.UTF_8);
        return java.security.MessageDigest.isEqual(aBytes, bBytes);
    }
}
