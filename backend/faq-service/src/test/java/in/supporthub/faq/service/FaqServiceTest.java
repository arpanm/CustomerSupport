package in.supporthub.faq.service;

import in.supporthub.faq.domain.FaqEntry;
import in.supporthub.faq.dto.CreateFaqRequest;
import in.supporthub.faq.dto.FaqResponse;
import in.supporthub.faq.dto.FaqSearchResult;
import in.supporthub.faq.dto.StrapiEntry;
import in.supporthub.faq.dto.StrapiWebhookPayload;
import in.supporthub.faq.dto.UpdateFaqRequest;
import in.supporthub.faq.exception.FaqNotFoundException;
import in.supporthub.faq.repository.FaqRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link FaqService}, {@link SemanticSearchService},
 * and {@link StrapiWebhookService} covering the following requirements:
 *
 * <ul>
 *   <li>REQ-FAQ-ADMIN-01 — CRUD: create FAQ saves to repository with embedding generated</li>
 *   <li>REQ-FAQ-ADMIN-04 — Publish/unpublish toggle changes isPublished flag</li>
 *   <li>REQ-AI-EMBED-03  — Re-embedding triggered when FAQ question or answer changes</li>
 *   <li>REQ-FAQ-CMS-03   — Strapi sync upserts entries (update if exists, insert if not)</li>
 *   <li>REQ-CUI-FAQ-02   — Search returns results matching the query</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("FaqServiceTest")
class FaqServiceTest {

    // =========================================================================
    // Shared fixtures
    // =========================================================================

    private static final String WEBHOOK_SECRET = "test-webhook-secret-32-bytes-long!";
    private static final String HMAC_ALGORITHM  = "HmacSHA256";

    private UUID tenantId;
    private UUID faqId;

    // =========================================================================
    // REQ-FAQ-ADMIN-01, REQ-FAQ-ADMIN-04, REQ-AI-EMBED-03 — FaqService
    // =========================================================================

    @Nested
    @DisplayName("FaqService")
    class FaqServiceTests {

        @Mock
        private FaqRepository faqRepository;

        @Mock
        private EmbeddingService embeddingService;

        @InjectMocks
        private FaqService faqService;

        @BeforeEach
        void setUp() {
            tenantId = UUID.randomUUID();
            faqId    = UUID.randomUUID();
        }

        // ----- REQ-FAQ-ADMIN-01: create -----

        @Test
        @DisplayName("REQ-FAQ-ADMIN-01: createFaq — saves entity to repository with generated embedding")
        void createFaq_savesEntityWithEmbedding() {
            float[] embedding = new float[1536];
            embedding[0] = 0.42f;

            CreateFaqRequest request = new CreateFaqRequest(
                    "How do I reset my password?",
                    "Click the Forgot Password link on the login page.",
                    null,
                    List.of("account", "password")
            );

            when(embeddingService.generateEmbedding(request.question(), request.answer()))
                    .thenReturn(embedding);

            FaqEntry savedEntry = FaqEntry.builder()
                    .id(faqId)
                    .tenantId(tenantId)
                    .question(request.question())
                    .answer(request.answer())
                    .tags(new String[]{"account", "password"})
                    .isPublished(false)
                    .viewCount(0L)
                    .embedding(embedding)
                    .createdAt(OffsetDateTime.now())
                    .updatedAt(OffsetDateTime.now())
                    .build();

            when(faqRepository.save(any(FaqEntry.class))).thenReturn(savedEntry);

            FaqResponse response = faqService.createFaq(tenantId, request);

            // Embedding generated from question + answer
            verify(embeddingService).generateEmbedding(request.question(), request.answer());

            // Entity persisted
            ArgumentCaptor<FaqEntry> captor = ArgumentCaptor.forClass(FaqEntry.class);
            verify(faqRepository).save(captor.capture());

            FaqEntry persisted = captor.getValue();
            assertThat(persisted.getTenantId()).isEqualTo(tenantId);
            assertThat(persisted.getQuestion()).isEqualTo(request.question());
            assertThat(persisted.getAnswer()).isEqualTo(request.answer());
            assertThat(persisted.getEmbedding()).isEqualTo(embedding);
            assertThat(persisted.isPublished()).isFalse();

            // Response DTO reflects saved state
            assertThat(response.id()).isEqualTo(faqId);
            assertThat(response.question()).isEqualTo(request.question());
        }

        @Test
        @DisplayName("REQ-FAQ-ADMIN-01: createFaq — new FAQ is always created in unpublished state")
        void createFaq_newFaqIsUnpublished() {
            CreateFaqRequest request = new CreateFaqRequest(
                    "What is your return policy?",
                    "Items can be returned within 30 days.",
                    null,
                    null
            );

            when(embeddingService.generateEmbedding(anyString(), anyString()))
                    .thenReturn(new float[1536]);

            FaqEntry savedEntry = FaqEntry.builder()
                    .id(faqId)
                    .tenantId(tenantId)
                    .question(request.question())
                    .answer(request.answer())
                    .tags(new String[0])
                    .isPublished(false)
                    .viewCount(0L)
                    .createdAt(OffsetDateTime.now())
                    .updatedAt(OffsetDateTime.now())
                    .build();

            when(faqRepository.save(any(FaqEntry.class))).thenReturn(savedEntry);

            FaqResponse response = faqService.createFaq(tenantId, request);

            assertThat(response.isPublished()).isFalse();
        }

        @Test
        @DisplayName("REQ-FAQ-ADMIN-01: createFaq — embedding fails gracefully, FAQ still saved without embedding")
        void createFaq_embeddingFails_faqSavedWithoutEmbedding() {
            CreateFaqRequest request = new CreateFaqRequest(
                    "How do I contact support?",
                    "Email us at support@example.com.",
                    null,
                    null
            );

            // EmbeddingService returns empty array on failure (graceful degradation)
            when(embeddingService.generateEmbedding(anyString(), anyString()))
                    .thenReturn(new float[0]);

            FaqEntry savedEntry = FaqEntry.builder()
                    .id(faqId)
                    .tenantId(tenantId)
                    .question(request.question())
                    .answer(request.answer())
                    .tags(new String[0])
                    .isPublished(false)
                    .viewCount(0L)
                    .embedding(null)
                    .createdAt(OffsetDateTime.now())
                    .updatedAt(OffsetDateTime.now())
                    .build();

            when(faqRepository.save(any(FaqEntry.class))).thenReturn(savedEntry);

            FaqResponse response = faqService.createFaq(tenantId, request);

            // FAQ is still saved despite embedding failure
            ArgumentCaptor<FaqEntry> captor = ArgumentCaptor.forClass(FaqEntry.class);
            verify(faqRepository).save(captor.capture());

            // embedding field should be null when empty array is returned (length == 0 → null stored)
            assertThat(captor.getValue().getEmbedding()).isNull();
            assertThat(response.id()).isEqualTo(faqId);
        }

        @Test
        @DisplayName("REQ-FAQ-ADMIN-01: deleteFaq — repository.delete called for the correct entity")
        void deleteFaq_existingEntry_deletesEntity() {
            FaqEntry existing = FaqEntry.builder()
                    .id(faqId)
                    .tenantId(tenantId)
                    .question("How do I cancel?")
                    .answer("Call us to cancel.")
                    .build();

            when(faqRepository.findById(faqId)).thenReturn(Optional.of(existing));

            faqService.deleteFaq(tenantId, faqId);

            verify(faqRepository).delete(existing);
        }

        @Test
        @DisplayName("REQ-FAQ-ADMIN-01: deleteFaq — entry belonging to different tenant throws FaqNotFoundException")
        void deleteFaq_wrongTenant_throwsFaqNotFoundException() {
            UUID otherTenant = UUID.randomUUID();
            FaqEntry existing = FaqEntry.builder()
                    .id(faqId)
                    .tenantId(otherTenant)
                    .question("How do I cancel?")
                    .answer("Call us to cancel.")
                    .build();

            when(faqRepository.findById(faqId)).thenReturn(Optional.of(existing));

            assertThatThrownBy(() -> faqService.deleteFaq(tenantId, faqId))
                    .isInstanceOf(FaqNotFoundException.class);

            verify(faqRepository, never()).delete(any());
        }

        // ----- REQ-FAQ-ADMIN-04: publish / unpublish -----

        @Test
        @DisplayName("REQ-FAQ-ADMIN-04: publishFaq — sets isPublished=true on the persisted entry")
        void publishFaq_setsPublishedTrue() {
            FaqEntry existing = FaqEntry.builder()
                    .id(faqId)
                    .tenantId(tenantId)
                    .question("How do I track my order?")
                    .answer("Use the order tracking page.")
                    .isPublished(false)
                    .viewCount(0L)
                    .createdAt(OffsetDateTime.now())
                    .updatedAt(OffsetDateTime.now())
                    .build();

            when(faqRepository.findById(faqId)).thenReturn(Optional.of(existing));
            when(faqRepository.save(any(FaqEntry.class))).thenAnswer(inv -> inv.getArgument(0));

            FaqResponse response = faqService.publishFaq(tenantId, faqId);

            ArgumentCaptor<FaqEntry> captor = ArgumentCaptor.forClass(FaqEntry.class);
            verify(faqRepository).save(captor.capture());

            assertThat(captor.getValue().isPublished()).isTrue();
            assertThat(response.isPublished()).isTrue();
        }

        @Test
        @DisplayName("REQ-FAQ-ADMIN-04: publishFaq on already-published entry — isPublished remains true (idempotent)")
        void publishFaq_alreadyPublished_remainsPublishedTrue() {
            FaqEntry existingPublished = FaqEntry.builder()
                    .id(faqId)
                    .tenantId(tenantId)
                    .question("What are your hours?")
                    .answer("We are open 24/7.")
                    .isPublished(true)
                    .viewCount(0L)
                    .createdAt(OffsetDateTime.now())
                    .updatedAt(OffsetDateTime.now())
                    .build();

            when(faqRepository.findById(faqId)).thenReturn(Optional.of(existingPublished));
            when(faqRepository.save(any(FaqEntry.class))).thenAnswer(inv -> inv.getArgument(0));

            FaqResponse response = faqService.publishFaq(tenantId, faqId);

            assertThat(response.isPublished()).isTrue();
        }

        @Test
        @DisplayName("REQ-FAQ-ADMIN-04: publishFaq — throws FaqNotFoundException for unknown FAQ ID")
        void publishFaq_notFound_throwsFaqNotFoundException() {
            when(faqRepository.findById(faqId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> faqService.publishFaq(tenantId, faqId))
                    .isInstanceOf(FaqNotFoundException.class);

            verify(faqRepository, never()).save(any());
        }

        // ----- REQ-AI-EMBED-03: re-embedding on content change -----

        @Test
        @DisplayName("REQ-AI-EMBED-03: updateFaq — question changed — embedding is regenerated")
        void updateFaq_questionChanged_embeddingRegenerated() {
            FaqEntry existing = FaqEntry.builder()
                    .id(faqId)
                    .tenantId(tenantId)
                    .question("Old question text")
                    .answer("The same answer.")
                    .isPublished(false)
                    .embedding(new float[]{0.1f, 0.2f})
                    .viewCount(0L)
                    .createdAt(OffsetDateTime.now())
                    .updatedAt(OffsetDateTime.now())
                    .build();

            UpdateFaqRequest request = new UpdateFaqRequest(
                    "New question text",  // changed
                    null,                 // answer unchanged
                    null,
                    null
            );

            float[] newEmbedding = new float[1536];
            newEmbedding[0] = 0.99f;

            when(faqRepository.findById(faqId)).thenReturn(Optional.of(existing));
            when(embeddingService.generateEmbedding("New question text", "The same answer."))
                    .thenReturn(newEmbedding);
            when(faqRepository.save(any(FaqEntry.class))).thenAnswer(inv -> inv.getArgument(0));

            faqService.updateFaq(tenantId, faqId, request);

            verify(embeddingService).generateEmbedding("New question text", "The same answer.");

            ArgumentCaptor<FaqEntry> captor = ArgumentCaptor.forClass(FaqEntry.class);
            verify(faqRepository).save(captor.capture());
            assertThat(captor.getValue().getEmbedding()).isEqualTo(newEmbedding);
        }

        @Test
        @DisplayName("REQ-AI-EMBED-03: updateFaq — answer changed — embedding is regenerated")
        void updateFaq_answerChanged_embeddingRegenerated() {
            FaqEntry existing = FaqEntry.builder()
                    .id(faqId)
                    .tenantId(tenantId)
                    .question("How do I return an item?")
                    .answer("Old return instructions.")
                    .isPublished(false)
                    .embedding(new float[]{0.5f})
                    .viewCount(0L)
                    .createdAt(OffsetDateTime.now())
                    .updatedAt(OffsetDateTime.now())
                    .build();

            UpdateFaqRequest request = new UpdateFaqRequest(
                    null,                           // question unchanged
                    "Updated return instructions.",  // answer changed
                    null,
                    null
            );

            float[] newEmbedding = new float[1536];
            when(faqRepository.findById(faqId)).thenReturn(Optional.of(existing));
            when(embeddingService.generateEmbedding(
                    "How do I return an item?", "Updated return instructions."))
                    .thenReturn(newEmbedding);
            when(faqRepository.save(any(FaqEntry.class))).thenAnswer(inv -> inv.getArgument(0));

            faqService.updateFaq(tenantId, faqId, request);

            verify(embeddingService).generateEmbedding(
                    "How do I return an item?", "Updated return instructions.");
        }

        @Test
        @DisplayName("REQ-AI-EMBED-03: updateFaq — only tags changed — embedding is NOT regenerated")
        void updateFaq_onlyTagsChanged_embeddingNotRegenerated() {
            FaqEntry existing = FaqEntry.builder()
                    .id(faqId)
                    .tenantId(tenantId)
                    .question("Same question")
                    .answer("Same answer")
                    .isPublished(false)
                    .embedding(new float[]{0.1f})
                    .viewCount(0L)
                    .createdAt(OffsetDateTime.now())
                    .updatedAt(OffsetDateTime.now())
                    .build();

            UpdateFaqRequest request = new UpdateFaqRequest(
                    null,               // question unchanged
                    null,               // answer unchanged
                    null,
                    List.of("new-tag")  // only metadata changed
            );

            when(faqRepository.findById(faqId)).thenReturn(Optional.of(existing));
            when(faqRepository.save(any(FaqEntry.class))).thenAnswer(inv -> inv.getArgument(0));

            faqService.updateFaq(tenantId, faqId, request);

            verify(embeddingService, never()).generateEmbedding(anyString(), anyString());
        }

        @Test
        @DisplayName("REQ-AI-EMBED-03: updateFaq — question sent with identical value — embedding NOT regenerated")
        void updateFaq_questionSameValue_embeddingNotRegenerated() {
            String sameQuestion = "Unchanged question";
            FaqEntry existing = FaqEntry.builder()
                    .id(faqId)
                    .tenantId(tenantId)
                    .question(sameQuestion)
                    .answer("Some answer")
                    .isPublished(false)
                    .embedding(new float[]{0.3f})
                    .viewCount(0L)
                    .createdAt(OffsetDateTime.now())
                    .updatedAt(OffsetDateTime.now())
                    .build();

            // Same question text — service must detect no actual content change
            UpdateFaqRequest request = new UpdateFaqRequest(sameQuestion, null, null, null);

            when(faqRepository.findById(faqId)).thenReturn(Optional.of(existing));
            when(faqRepository.save(any(FaqEntry.class))).thenAnswer(inv -> inv.getArgument(0));

            faqService.updateFaq(tenantId, faqId, request);

            verify(embeddingService, never()).generateEmbedding(anyString(), anyString());
        }
    }

    // =========================================================================
    // REQ-FAQ-CMS-03 — StrapiWebhookService: upsert (insert or update)
    // =========================================================================

    @Nested
    @DisplayName("StrapiWebhookService — REQ-FAQ-CMS-03 upsert")
    class StrapiUpsertTests {

        @Mock
        private FaqRepository faqRepository;

        @Mock
        private EmbeddingService embeddingService;

        @InjectMocks
        private StrapiWebhookService strapiWebhookService;

        private StrapiEntry strapiEntry;

        @BeforeEach
        void setUp() {
            ReflectionTestUtils.setField(strapiWebhookService, "webhookSecret", WEBHOOK_SECRET);
            tenantId = UUID.randomUUID();
            strapiEntry = new StrapiEntry(
                    "cms-entry-001",
                    "What is your refund policy?",
                    "Full refund within 14 days.",
                    "billing",
                    List.of("refund", "billing"),
                    null
            );
        }

        @Test
        @DisplayName("REQ-FAQ-CMS-03: entry.create — inserts new FaqEntry when strapiId not found (insert path)")
        void strapiSync_create_insertsNewEntryWhenNotFound() {
            String rawBody = buildRawBody("entry.create");
            String signature = computeHmac(WEBHOOK_SECRET, rawBody);
            StrapiWebhookPayload payload = new StrapiWebhookPayload("entry.create", strapiEntry);

            when(faqRepository.findByTenantIdAndStrapiId(tenantId, strapiEntry.id()))
                    .thenReturn(Optional.empty());
            when(embeddingService.generateEmbedding(anyString(), anyString()))
                    .thenReturn(new float[1536]);

            strapiWebhookService.handleWebhook(payload, rawBody, signature, tenantId);

            ArgumentCaptor<FaqEntry> captor = ArgumentCaptor.forClass(FaqEntry.class);
            verify(faqRepository).save(captor.capture());

            FaqEntry saved = captor.getValue();
            assertThat(saved.getStrapiId()).isEqualTo(strapiEntry.id());
            assertThat(saved.getTenantId()).isEqualTo(tenantId);
            assertThat(saved.getQuestion()).isEqualTo(strapiEntry.question());
            assertThat(saved.getAnswer()).isEqualTo(strapiEntry.answer());
        }

        @Test
        @DisplayName("REQ-FAQ-CMS-03: entry.update — updates existing FaqEntry when strapiId found (update path)")
        void strapiSync_update_updatesExistingEntryWhenFound() {
            UUID existingId = UUID.randomUUID();
            FaqEntry existingEntry = FaqEntry.builder()
                    .id(existingId)
                    .tenantId(tenantId)
                    .strapiId(strapiEntry.id())
                    .question("Old question")
                    .answer("Old answer")
                    .isPublished(false)
                    .embedding(new float[]{0.1f, 0.2f})
                    .build();

            String rawBody = buildRawBody("entry.update");
            String signature = computeHmac(WEBHOOK_SECRET, rawBody);
            StrapiWebhookPayload payload = new StrapiWebhookPayload("entry.update", strapiEntry);

            when(faqRepository.findByTenantIdAndStrapiId(tenantId, strapiEntry.id()))
                    .thenReturn(Optional.of(existingEntry));
            when(embeddingService.generateEmbedding(anyString(), anyString()))
                    .thenReturn(new float[1536]);

            strapiWebhookService.handleWebhook(payload, rawBody, signature, tenantId);

            ArgumentCaptor<FaqEntry> captor = ArgumentCaptor.forClass(FaqEntry.class);
            verify(faqRepository).save(captor.capture());

            FaqEntry updated = captor.getValue();
            // Existing entity identity preserved (same DB id)
            assertThat(updated.getId()).isEqualTo(existingId);
            // Content updated from Strapi payload
            assertThat(updated.getQuestion()).isEqualTo(strapiEntry.question());
            assertThat(updated.getAnswer()).isEqualTo(strapiEntry.answer());
        }

        @Test
        @DisplayName("REQ-FAQ-CMS-03: entry.update — upsert regenerates embedding when content differs")
        void strapiSync_update_regeneratesEmbeddingOnContentChange() {
            FaqEntry existingEntry = FaqEntry.builder()
                    .id(UUID.randomUUID())
                    .tenantId(tenantId)
                    .strapiId(strapiEntry.id())
                    .question("Old question")
                    .answer("Old answer")
                    .isPublished(false)
                    .embedding(new float[]{0.5f})
                    .build();

            String rawBody = buildRawBody("entry.update");
            String signature = computeHmac(WEBHOOK_SECRET, rawBody);
            StrapiWebhookPayload payload = new StrapiWebhookPayload("entry.update", strapiEntry);

            when(faqRepository.findByTenantIdAndStrapiId(tenantId, strapiEntry.id()))
                    .thenReturn(Optional.of(existingEntry));
            when(embeddingService.generateEmbedding(anyString(), anyString()))
                    .thenReturn(new float[1536]);

            strapiWebhookService.handleWebhook(payload, rawBody, signature, tenantId);

            verify(embeddingService).generateEmbedding(strapiEntry.question(), strapiEntry.answer());
        }

        @Test
        @DisplayName("REQ-FAQ-CMS-03: entry.publish — upserted entry has isPublished=true")
        void strapiSync_publish_setsPublishedTrue() {
            String rawBody = buildRawBody("entry.publish");
            String signature = computeHmac(WEBHOOK_SECRET, rawBody);
            StrapiWebhookPayload payload = new StrapiWebhookPayload("entry.publish", strapiEntry);

            when(faqRepository.findByTenantIdAndStrapiId(tenantId, strapiEntry.id()))
                    .thenReturn(Optional.empty());
            when(embeddingService.generateEmbedding(anyString(), anyString()))
                    .thenReturn(new float[1536]);

            strapiWebhookService.handleWebhook(payload, rawBody, signature, tenantId);

            ArgumentCaptor<FaqEntry> captor = ArgumentCaptor.forClass(FaqEntry.class);
            verify(faqRepository).save(captor.capture());
            assertThat(captor.getValue().isPublished()).isTrue();
        }

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
            return "{\"event\":\"" + event + "\",\"entry\":{\"id\":\"" + strapiEntry.id() + "\"}}";
        }
    }

    // =========================================================================
    // REQ-CUI-FAQ-02 — SemanticSearchService: search returns matching results
    // =========================================================================

    @Nested
    @DisplayName("SemanticSearchService — REQ-CUI-FAQ-02 search returns matching results")
    class SearchMatchingResultsTests {

        @Mock
        private EmbeddingService embeddingService;

        @Mock
        private FaqRepository faqRepository;

        @InjectMocks
        private SemanticSearchService semanticSearchService;

        @BeforeEach
        void setUp() {
            tenantId = UUID.randomUUID();
        }

        @Test
        @DisplayName("REQ-CUI-FAQ-02: search — semantic path — returns FAQ entries matching the query")
        void search_semanticPath_returnsMatchingResults() {
            float[] embedding = new float[1536];
            embedding[0] = 0.7f;

            FaqEntry match1 = FaqEntry.builder()
                    .id(UUID.randomUUID())
                    .tenantId(tenantId)
                    .question("How do I reset my password?")
                    .answer("Click Forgot Password on the login page.")
                    .isPublished(true)
                    .build();

            FaqEntry match2 = FaqEntry.builder()
                    .id(UUID.randomUUID())
                    .tenantId(tenantId)
                    .question("Where can I change my email address?")
                    .answer("Go to account settings.")
                    .isPublished(true)
                    .build();

            when(embeddingService.generateQueryEmbedding("account access")).thenReturn(embedding);
            when(faqRepository.semanticSearch(eq(tenantId), anyString(), eq(5)))
                    .thenReturn(List.of(match1, match2));

            List<FaqSearchResult> results = semanticSearchService.search(tenantId, "account access", 5);

            assertThat(results).hasSize(2);
            assertThat(results.get(0).question()).isEqualTo("How do I reset my password?");
            assertThat(results.get(1).question()).isEqualTo("Where can I change my email address?");
            // Semantic path always assigns similarityScore = 1.0
            assertThat(results).allMatch(r -> r.similarityScore() == 1.0);
        }

        @Test
        @DisplayName("REQ-CUI-FAQ-02: search — keyword fallback path — returns keyword-matching FAQ entries")
        void search_keywordFallback_returnsMatchingResults() {
            FaqEntry match = FaqEntry.builder()
                    .id(UUID.randomUUID())
                    .tenantId(tenantId)
                    .question("How do I track my shipment?")
                    .answer("Use the tracking page with your order number.")
                    .isPublished(true)
                    .build();

            // Simulate embedding unavailability — empty array triggers keyword fallback
            when(embeddingService.generateQueryEmbedding(anyString())).thenReturn(new float[0]);
            when(faqRepository.keywordSearch(eq(tenantId), eq("track"), eq(3)))
                    .thenReturn(List.of(match));

            List<FaqSearchResult> results = semanticSearchService.search(tenantId, "track", 3);

            assertThat(results).hasSize(1);
            assertThat(results.get(0).question()).isEqualTo("How do I track my shipment?");
        }

        @Test
        @DisplayName("REQ-CUI-FAQ-02: search — no matching FAQs — returns empty list")
        void search_noMatchingFaqs_returnsEmptyList() {
            float[] embedding = new float[1536];
            when(embeddingService.generateQueryEmbedding(anyString())).thenReturn(embedding);
            when(faqRepository.semanticSearch(eq(tenantId), anyString(), eq(5)))
                    .thenReturn(List.of());

            List<FaqSearchResult> results = semanticSearchService.search(tenantId, "xyzzy-no-match", 5);

            assertThat(results).isEmpty();
        }

        @Test
        @DisplayName("REQ-CUI-FAQ-02: search — result count is bounded by the requested limit parameter")
        void search_resultsBoundedByLimit() {
            float[] embedding = new float[1536];
            when(embeddingService.generateQueryEmbedding(anyString())).thenReturn(embedding);
            when(faqRepository.semanticSearch(eq(tenantId), anyString(), eq(2)))
                    .thenReturn(List.of(
                            FaqEntry.builder().id(UUID.randomUUID()).tenantId(tenantId)
                                    .question("Q1").answer("A1").build(),
                            FaqEntry.builder().id(UUID.randomUUID()).tenantId(tenantId)
                                    .question("Q2").answer("A2").build()
                    ));

            List<FaqSearchResult> results = semanticSearchService.search(tenantId, "any query", 2);

            assertThat(results).hasSize(2);
            verify(faqRepository).semanticSearch(eq(tenantId), anyString(), eq(2));
            // keywordSearch must never be called on the semantic path
            verify(faqRepository, never()).keywordSearch(any(), anyString(), anyInt());
        }
    }
}
