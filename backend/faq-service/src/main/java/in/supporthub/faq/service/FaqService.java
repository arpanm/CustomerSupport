package in.supporthub.faq.service;

import in.supporthub.faq.domain.FaqEntry;
import in.supporthub.faq.dto.CreateFaqRequest;
import in.supporthub.faq.dto.FaqResponse;
import in.supporthub.faq.dto.UpdateFaqRequest;
import in.supporthub.faq.exception.FaqNotFoundException;
import in.supporthub.faq.repository.FaqRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Business logic for FAQ CRUD operations.
 *
 * <p>All write methods are {@link Transactional}. Read methods use
 * {@code readOnly = true} for performance and to prevent accidental writes.
 *
 * <p>Tenant isolation: every method receives a {@code tenantId} sourced from
 * {@code TenantContextHolder} in the controller — never from user input.
 *
 * <p>Embedding strategy:
 * <ul>
 *   <li>Generated on create — if it fails, the FAQ is still saved (degraded state).</li>
 *   <li>Regenerated on update only when {@code question} or {@code answer} changes.</li>
 * </ul>
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class FaqService {

    private final FaqRepository faqRepository;
    private final EmbeddingService embeddingService;

    /**
     * Creates a new FAQ entry in unpublished state.
     *
     * @param tenantId the current tenant (from TenantContextHolder)
     * @param request  validated create request
     * @return the persisted FAQ as a response DTO
     */
    public FaqResponse createFaq(UUID tenantId, CreateFaqRequest request) {
        float[] embedding = embeddingService.generateEmbedding(request.question(), request.answer());

        FaqEntry entry = FaqEntry.builder()
                .tenantId(tenantId)
                .categoryId(request.categoryId())
                .question(request.question())
                .answer(request.answer())
                .tags(request.tags() != null
                        ? request.tags().toArray(new String[0])
                        : new String[0])
                .isPublished(false)
                .viewCount(0L)
                .embedding(embedding.length > 0 ? embedding : null)
                .build();

        FaqEntry saved = faqRepository.save(entry);
        log.info("FAQ created: id={}, tenantId={}", saved.getId(), tenantId);
        return toResponse(saved);
    }

    /**
     * Updates an existing FAQ entry. Regenerates the embedding if question or answer changed.
     *
     * @param tenantId the current tenant
     * @param id       UUID of the FAQ entry
     * @param request  partial update — null fields are ignored
     * @return updated FAQ as a response DTO
     * @throws FaqNotFoundException if the entry does not exist for this tenant
     */
    public FaqResponse updateFaq(UUID tenantId, UUID id, UpdateFaqRequest request) {
        FaqEntry entry = findForTenant(tenantId, id);

        boolean contentChanged = false;

        if (request.question() != null && !request.question().equals(entry.getQuestion())) {
            entry.setQuestion(request.question());
            contentChanged = true;
        }
        if (request.answer() != null && !request.answer().equals(entry.getAnswer())) {
            entry.setAnswer(request.answer());
            contentChanged = true;
        }
        if (request.categoryId() != null) {
            entry.setCategoryId(request.categoryId());
        }
        if (request.tags() != null) {
            entry.setTags(request.tags().toArray(new String[0]));
        }

        if (contentChanged) {
            float[] embedding = embeddingService.generateEmbedding(entry.getQuestion(), entry.getAnswer());
            if (embedding.length > 0) {
                entry.setEmbedding(embedding);
            }
            log.info("FAQ content changed, embedding regenerated: id={}, tenantId={}", id, tenantId);
        }

        FaqEntry saved = faqRepository.save(entry);
        log.info("FAQ updated: id={}, tenantId={}", saved.getId(), tenantId);
        return toResponse(saved);
    }

    /**
     * Permanently deletes a FAQ entry.
     *
     * @param tenantId the current tenant
     * @param id       UUID of the FAQ entry to delete
     * @throws FaqNotFoundException if the entry does not exist for this tenant
     */
    public void deleteFaq(UUID tenantId, UUID id) {
        FaqEntry entry = findForTenant(tenantId, id);
        faqRepository.delete(entry);
        log.info("FAQ deleted: id={}, tenantId={}", id, tenantId);
    }

    /**
     * Publishes a FAQ entry, making it visible in search results.
     *
     * @param tenantId the current tenant
     * @param id       UUID of the FAQ entry to publish
     * @return updated FAQ as a response DTO
     */
    public FaqResponse publishFaq(UUID tenantId, UUID id) {
        FaqEntry entry = findForTenant(tenantId, id);
        entry.setPublished(true);
        FaqEntry saved = faqRepository.save(entry);
        log.info("FAQ published: id={}, tenantId={}", id, tenantId);
        return toResponse(saved);
    }

    /**
     * Retrieves a single FAQ entry by ID.
     *
     * @param tenantId the current tenant
     * @param id       UUID of the FAQ entry
     * @return the FAQ as a response DTO
     * @throws FaqNotFoundException if not found
     */
    @Transactional(readOnly = true)
    public FaqResponse getFaq(UUID tenantId, UUID id) {
        return toResponse(findForTenant(tenantId, id));
    }

    /**
     * Lists FAQ entries for a tenant with optional category filter.
     *
     * @param tenantId   the current tenant
     * @param categoryId optional category filter; null returns all categories
     * @param pageable   pagination and sort parameters
     * @return paginated list of FAQ responses
     */
    @Transactional(readOnly = true)
    public Page<FaqResponse> listFaqs(UUID tenantId, UUID categoryId, Pageable pageable) {
        Page<FaqEntry> page;
        if (categoryId != null) {
            page = faqRepository.findByTenantIdAndCategoryId(tenantId, categoryId, pageable);
        } else {
            page = faqRepository.findByTenantId(tenantId, pageable);
        }
        return page.map(this::toResponse);
    }

    /**
     * Increments the view count for a FAQ entry.
     * Uses a direct UPDATE query to avoid read-modify-write race conditions.
     *
     * @param tenantId the current tenant
     * @param id       UUID of the FAQ entry
     */
    public void incrementViewCount(UUID tenantId, UUID id) {
        faqRepository.incrementViewCount(tenantId, id);
        log.debug("View count incremented: id={}, tenantId={}", id, tenantId);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private FaqEntry findForTenant(UUID tenantId, UUID id) {
        return faqRepository.findById(id)
                .filter(entry -> tenantId.equals(entry.getTenantId()))
                .orElseThrow(() -> {
                    log.warn("FAQ not found: id={}, tenantId={}", id, tenantId);
                    return new FaqNotFoundException(id);
                });
    }

    private FaqResponse toResponse(FaqEntry entry) {
        List<String> tags = entry.getTags() != null
                ? Arrays.asList(entry.getTags())
                : List.of();
        return new FaqResponse(
                entry.getId(),
                entry.getTenantId(),
                entry.getCategoryId(),
                entry.getQuestion(),
                entry.getAnswer(),
                tags,
                entry.isPublished(),
                entry.getViewCount(),
                entry.getCreatedAt(),
                entry.getUpdatedAt()
        );
    }
}
