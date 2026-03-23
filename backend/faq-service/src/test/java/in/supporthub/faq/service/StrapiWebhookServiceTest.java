package in.supporthub.faq.service;

import in.supporthub.faq.domain.FaqEntry;
import in.supporthub.faq.dto.StrapiEntry;
import in.supporthub.faq.dto.StrapiWebhookPayload;
import in.supporthub.faq.exception.WebhookAuthException;
import in.supporthub.faq.repository.FaqRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link StrapiWebhookService}.
 *
 * <p>Tests cover:
 * <ul>
 *   <li>Valid HMAC → event is processed</li>
 *   <li>Invalid HMAC → {@link WebhookAuthException} is thrown</li>
 *   <li>Missing / blank webhook secret → {@link WebhookAuthException} is thrown</li>
 *   <li>{@code entry.create} → new FaqEntry created with isPublished=false</li>
 *   <li>{@code entry.publish} → FaqEntry upserted with isPublished=true</li>
 *   <li>{@code entry.update} → existing FaqEntry updated, embedding regenerated</li>
 *   <li>{@code entry.delete} → existing FaqEntry set to isPublished=false</li>
 *   <li>{@code entry.unpublish} → existing FaqEntry set to isPublished=false</li>
 *   <li>Unknown event type → silently ignored</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("StrapiWebhookService")
class StrapiWebhookServiceTest {

    private static final String WEBHOOK_SECRET = "test-webhook-secret-32-bytes-long!";
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    @Mock
    private FaqRepository faqRepository;

    @Mock
    private EmbeddingService embeddingService;

    @InjectMocks
    private StrapiWebhookService strapiWebhookService;

    private UUID tenantId;
    private StrapiEntry sampleEntry;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(strapiWebhookService, "webhookSecret", WEBHOOK_SECRET);
        tenantId = UUID.randomUUID();
        sampleEntry = new StrapiEntry(
                "strapi-id-123",
                "How do I track my order?",
                "You can track your order via the order tracking page.",
                "orders",
                List.of("order", "tracking"),
                null
        );
    }

    // =========================================================================
    // HMAC validation
    // =========================================================================

    @Test
    @DisplayName("handleWebhook — valid HMAC signature — processes the event")
    void handleWebhook_validSignature_processesEvent() {
        String rawBody = "{\"event\":\"entry.create\",\"entry\":{\"id\":\"strapi-id-123\"}}";
        String validSignature = computeHmac(WEBHOOK_SECRET, rawBody);

        StrapiWebhookPayload payload = new StrapiWebhookPayload("entry.create", sampleEntry);
        when(faqRepository.findByTenantIdAndStrapiId(tenantId, "strapi-id-123"))
                .thenReturn(Optional.empty());
        when(embeddingService.generateEmbedding(anyString(), anyString()))
                .thenReturn(new float[1536]);

        strapiWebhookService.handleWebhook(payload, rawBody, validSignature, tenantId);

        verify(faqRepository).save(any(FaqEntry.class));
    }

    @Test
    @DisplayName("handleWebhook — invalid HMAC signature — throws WebhookAuthException")
    void handleWebhook_invalidSignature_throwsWebhookAuthException() {
        String rawBody = "{\"event\":\"entry.create\",\"entry\":{}}";
        String invalidSignature = "deadbeefdeadbeefdeadbeefdeadbeef";
        StrapiWebhookPayload payload = new StrapiWebhookPayload("entry.create", sampleEntry);

        assertThatThrownBy(() ->
                strapiWebhookService.handleWebhook(payload, rawBody, invalidSignature, tenantId))
                .isInstanceOf(WebhookAuthException.class);

        verify(faqRepository, never()).save(any());
    }

    @Test
    @DisplayName("handleWebhook — blank webhook secret — throws WebhookAuthException")
    void handleWebhook_blankSecret_throwsWebhookAuthException() {
        ReflectionTestUtils.setField(strapiWebhookService, "webhookSecret", "");
        String rawBody = "{}";
        String sig = "anything";
        StrapiWebhookPayload payload = new StrapiWebhookPayload("entry.create", sampleEntry);

        assertThatThrownBy(() ->
                strapiWebhookService.handleWebhook(payload, rawBody, sig, tenantId))
                .isInstanceOf(WebhookAuthException.class)
                .hasMessageContaining("not configured");
    }

    // =========================================================================
    // Event: entry.create
    // =========================================================================

    @Test
    @DisplayName("handleWebhook — entry.create — creates new FaqEntry with isPublished=false")
    void handleWebhook_entryCreate_createsUnpublishedEntry() {
        String rawBody = buildRawBody("entry.create");
        String signature = computeHmac(WEBHOOK_SECRET, rawBody);
        StrapiWebhookPayload payload = new StrapiWebhookPayload("entry.create", sampleEntry);

        when(faqRepository.findByTenantIdAndStrapiId(tenantId, sampleEntry.id()))
                .thenReturn(Optional.empty());
        when(embeddingService.generateEmbedding(anyString(), anyString()))
                .thenReturn(new float[1536]);

        strapiWebhookService.handleWebhook(payload, rawBody, signature, tenantId);

        ArgumentCaptor<FaqEntry> captor = ArgumentCaptor.forClass(FaqEntry.class);
        verify(faqRepository).save(captor.capture());

        FaqEntry saved = captor.getValue();
        assertThat(saved.isPublished()).isFalse();
        assertThat(saved.getTenantId()).isEqualTo(tenantId);
        assertThat(saved.getStrapiId()).isEqualTo(sampleEntry.id());
        assertThat(saved.getQuestion()).isEqualTo(sampleEntry.question());
    }

    // =========================================================================
    // Event: entry.publish
    // =========================================================================

    @Test
    @DisplayName("handleWebhook — entry.publish — upserts with isPublished=true")
    void handleWebhook_entryPublish_setsPublishedTrue() {
        String rawBody = buildRawBody("entry.publish");
        String signature = computeHmac(WEBHOOK_SECRET, rawBody);
        StrapiWebhookPayload payload = new StrapiWebhookPayload("entry.publish", sampleEntry);

        when(faqRepository.findByTenantIdAndStrapiId(tenantId, sampleEntry.id()))
                .thenReturn(Optional.empty());
        when(embeddingService.generateEmbedding(anyString(), anyString()))
                .thenReturn(new float[1536]);

        strapiWebhookService.handleWebhook(payload, rawBody, signature, tenantId);

        ArgumentCaptor<FaqEntry> captor = ArgumentCaptor.forClass(FaqEntry.class);
        verify(faqRepository).save(captor.capture());

        assertThat(captor.getValue().isPublished()).isTrue();
    }

    // =========================================================================
    // Event: entry.delete
    // =========================================================================

    @Test
    @DisplayName("handleWebhook — entry.delete — sets isPublished=false on existing entry")
    void handleWebhook_entryDelete_unpublishesExistingEntry() {
        String rawBody = buildRawBody("entry.delete");
        String signature = computeHmac(WEBHOOK_SECRET, rawBody);
        StrapiWebhookPayload payload = new StrapiWebhookPayload("entry.delete", sampleEntry);

        FaqEntry existingEntry = FaqEntry.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .strapiId(sampleEntry.id())
                .question(sampleEntry.question())
                .answer(sampleEntry.answer())
                .isPublished(true)
                .build();

        when(faqRepository.findByTenantIdAndStrapiId(tenantId, sampleEntry.id()))
                .thenReturn(Optional.of(existingEntry));

        strapiWebhookService.handleWebhook(payload, rawBody, signature, tenantId);

        ArgumentCaptor<FaqEntry> captor = ArgumentCaptor.forClass(FaqEntry.class);
        verify(faqRepository).save(captor.capture());
        assertThat(captor.getValue().isPublished()).isFalse();
    }

    @Test
    @DisplayName("handleWebhook — entry.delete — unknown strapiId — no save called")
    void handleWebhook_entryDelete_unknownStrapiId_noSave() {
        String rawBody = buildRawBody("entry.delete");
        String signature = computeHmac(WEBHOOK_SECRET, rawBody);
        StrapiWebhookPayload payload = new StrapiWebhookPayload("entry.delete", sampleEntry);

        when(faqRepository.findByTenantIdAndStrapiId(tenantId, sampleEntry.id()))
                .thenReturn(Optional.empty());

        strapiWebhookService.handleWebhook(payload, rawBody, signature, tenantId);

        verify(faqRepository, never()).save(any());
    }

    // =========================================================================
    // Event: entry.unpublish
    // =========================================================================

    @Test
    @DisplayName("handleWebhook — entry.unpublish — sets isPublished=false")
    void handleWebhook_entryUnpublish_setsPublishedFalse() {
        String rawBody = buildRawBody("entry.unpublish");
        String signature = computeHmac(WEBHOOK_SECRET, rawBody);
        StrapiWebhookPayload payload = new StrapiWebhookPayload("entry.unpublish", sampleEntry);

        FaqEntry existingEntry = FaqEntry.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .strapiId(sampleEntry.id())
                .question(sampleEntry.question())
                .answer(sampleEntry.answer())
                .isPublished(true)
                .build();

        when(faqRepository.findByTenantIdAndStrapiId(tenantId, sampleEntry.id()))
                .thenReturn(Optional.of(existingEntry));

        strapiWebhookService.handleWebhook(payload, rawBody, signature, tenantId);

        ArgumentCaptor<FaqEntry> captor = ArgumentCaptor.forClass(FaqEntry.class);
        verify(faqRepository).save(captor.capture());
        assertThat(captor.getValue().isPublished()).isFalse();
    }

    // =========================================================================
    // Event: entry.update
    // =========================================================================

    @Test
    @DisplayName("handleWebhook — entry.update — updates existing entry and regenerates embedding")
    void handleWebhook_entryUpdate_updatesAndRegeneratesEmbedding() {
        String rawBody = buildRawBody("entry.update");
        String signature = computeHmac(WEBHOOK_SECRET, rawBody);
        StrapiWebhookPayload payload = new StrapiWebhookPayload("entry.update", sampleEntry);

        FaqEntry existingEntry = FaqEntry.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .strapiId(sampleEntry.id())
                .question("Old question")
                .answer("Old answer")
                .isPublished(false)
                .embedding(new float[1536])
                .build();

        when(faqRepository.findByTenantIdAndStrapiId(tenantId, sampleEntry.id()))
                .thenReturn(Optional.of(existingEntry));
        when(embeddingService.generateEmbedding(anyString(), anyString()))
                .thenReturn(new float[1536]);

        strapiWebhookService.handleWebhook(payload, rawBody, signature, tenantId);

        verify(embeddingService).generateEmbedding(sampleEntry.question(), sampleEntry.answer());
        ArgumentCaptor<FaqEntry> captor = ArgumentCaptor.forClass(FaqEntry.class);
        verify(faqRepository).save(captor.capture());
        assertThat(captor.getValue().getQuestion()).isEqualTo(sampleEntry.question());
    }

    // =========================================================================
    // Unknown event
    // =========================================================================

    @Test
    @DisplayName("handleWebhook — unknown event — silently ignored, no save called")
    void handleWebhook_unknownEvent_silentlyIgnored() {
        String rawBody = buildRawBody("entry.something-custom");
        String signature = computeHmac(WEBHOOK_SECRET, rawBody);
        StrapiWebhookPayload payload = new StrapiWebhookPayload("entry.something-custom", sampleEntry);

        strapiWebhookService.handleWebhook(payload, rawBody, signature, tenantId);

        verify(faqRepository, never()).save(any());
    }

    // =========================================================================
    // Test helpers
    // =========================================================================

    private String computeHmac(String secret, String data) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            SecretKeySpec keySpec = new SecretKeySpec(
                    secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
            mac.init(keySpec);
            byte[] result = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(result);
        } catch (Exception ex) {
            throw new RuntimeException("HMAC computation failed in test", ex);
        }
    }

    private String buildRawBody(String event) {
        return "{\"event\":\"" + event + "\",\"entry\":{\"id\":\"" + sampleEntry.id() + "\"}}";
    }
}
