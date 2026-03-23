package in.supporthub.ticket.service;

import in.supporthub.shared.event.TicketActivityAddedEvent;
import in.supporthub.shared.event.TicketCreatedEvent;
import in.supporthub.shared.event.TicketStatusChangedEvent;
import in.supporthub.ticket.domain.ActivityType;
import in.supporthub.ticket.domain.ActorType;
import in.supporthub.ticket.domain.Priority;
import in.supporthub.ticket.domain.SentimentLabel;
import in.supporthub.ticket.domain.Ticket;
import in.supporthub.ticket.domain.TicketActivity;
import in.supporthub.ticket.domain.TicketCategory;
import in.supporthub.ticket.domain.TicketStatus;
import in.supporthub.ticket.dto.AddActivityRequest;
import in.supporthub.ticket.dto.CreateTicketRequest;
import in.supporthub.ticket.dto.ResolveTicketRequest;
import in.supporthub.ticket.dto.ReopenTicketRequest;
import in.supporthub.ticket.dto.TicketFilterRequest;
import in.supporthub.ticket.dto.UpdateTicketRequest;
import in.supporthub.ticket.exception.CategoryNotFoundException;
import in.supporthub.ticket.exception.InvalidStatusTransitionException;
import in.supporthub.ticket.exception.TicketNotFoundException;
import in.supporthub.ticket.repository.SlaPolicyRepository;
import in.supporthub.ticket.repository.TicketActivityRepository;
import in.supporthub.ticket.repository.TicketCategoryRepository;
import in.supporthub.ticket.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Core business logic for ticket lifecycle management.
 *
 * <p>Orchestrates: ticket creation, status transitions, SLA computation,
 * activity recording, and event publishing.
 *
 * <p>All public methods accept {@code tenantId} explicitly (extracted from
 * the request context by the controller) to ensure proper tenant isolation.
 * The {@code TenantContextHolder} is used by the filter layer to set RLS context.
 */
@Service
@Transactional
@Slf4j
@RequiredArgsConstructor
public class TicketService {

    /** Default tenant ticket prefix when no tenant-specific prefix is configured. */
    private static final String DEFAULT_TICKET_PREFIX = "SUP";

    /** Default SLA hours when no matching policy is found. */
    private static final int DEFAULT_FIRST_RESPONSE_HOURS = 4;
    private static final int DEFAULT_RESOLUTION_HOURS = 24;

    private final TicketRepository ticketRepository;
    private final TicketActivityRepository activityRepository;
    private final TicketCategoryRepository categoryRepository;
    private final SlaPolicyRepository slaPolicyRepository;
    private final TicketNumberGenerator ticketNumberGenerator;
    private final SlaEngine slaEngine;
    private final TicketEventPublisher eventPublisher;

    // =========================================================================
    // TICKET CREATION
    // =========================================================================

    /**
     * Creates a new support ticket for a customer.
     *
     * <p>Steps:
     * <ol>
     *   <li>Validates the category exists and is active for the tenant.</li>
     *   <li>Generates a unique ticket number via Redis INCR.</li>
     *   <li>Resolves the SLA policy and computes deadlines.</li>
     *   <li>Persists the ticket entity.</li>
     *   <li>Records a SYSTEM activity "Ticket created".</li>
     *   <li>Publishes {@code ticket.created} Kafka event.</li>
     * </ol>
     *
     * @param request    validated creation request
     * @param customerId UUID of the authenticated customer
     * @param tenantId   UUID of the tenant (from request context)
     * @return the persisted ticket
     */
    public Ticket create(CreateTicketRequest request, UUID customerId, UUID tenantId) {
        // 1. Validate category
        TicketCategory category = categoryRepository.findById(request.categoryId())
                .filter(c -> c.getTenantId().equals(tenantId) && c.isActive())
                .orElseThrow(() -> new CategoryNotFoundException(request.categoryId()));

        // 2. Generate ticket number
        String ticketNumber = ticketNumberGenerator.generate(tenantId, DEFAULT_TICKET_PREFIX);

        // 3. Resolve SLA policy
        int firstResponseHours = DEFAULT_FIRST_RESPONSE_HOURS;
        int resolutionHours = DEFAULT_RESOLUTION_HOURS;

        var slaPolicies = slaPolicyRepository
                .findByTenantIdAndCategoryIdAndActive(tenantId, request.categoryId(), true);
        if (!slaPolicies.isEmpty()) {
            firstResponseHours = slaPolicies.get(0).getFirstResponseHours();
            resolutionHours = slaPolicies.get(0).getResolutionHours();
        } else {
            // Fall back to category-level SLA thresholds
            firstResponseHours = category.getSlaFirstResponseHours();
            resolutionHours = category.getSlaResolutionHours();
        }

        // 4. Build ticket (createdAt is set by @CreationTimestamp, but we need it for SLA computation)
        Instant now = Instant.now();
        Ticket ticket = Ticket.builder()
                .ticketNumber(ticketNumber)
                .tenantId(tenantId)
                .customerId(customerId)
                .orderId(request.orderId())
                .title(request.title())
                .description(request.description())
                .categoryId(request.categoryId())
                .subCategoryId(request.subCategoryId())
                .ticketType(request.ticketType() != null ? request.ticketType()
                        : in.supporthub.ticket.domain.TicketType.INQUIRY)
                .priority(category.getDefaultPriority())
                .status(TicketStatus.OPEN)
                .channel(request.channel() != null ? request.channel()
                        : in.supporthub.ticket.domain.Channel.WEB)
                .createdAt(now)
                .build();

        // 5. Compute SLA deadlines (requires createdAt to be set)
        SlaDeadlines deadlines = slaEngine.compute(ticket, firstResponseHours, resolutionHours);
        ticket.setSlaFirstResponseDueAt(deadlines.firstResponseDueAt());
        ticket.setSlaResolutionDueAt(deadlines.resolutionDueAt());

        Ticket saved = ticketRepository.save(ticket);

        // 6. Record system activity
        createSystemActivity(saved, "Ticket created via " + saved.getChannel(), tenantId);

        // 7. Publish event
        TicketCreatedEvent event = new TicketCreatedEvent(
                UUID.randomUUID().toString(),
                tenantId.toString(),
                null,
                Instant.now(),
                new TicketCreatedEvent.Payload(
                        saved.getId().toString(),
                        saved.getTicketNumber(),
                        customerId.toString(),
                        saved.getCategoryId().toString(),
                        saved.getSubCategoryId() != null ? saved.getSubCategoryId().toString() : null,
                        saved.getTitle(),
                        truncate(saved.getDescription(), 500),
                        saved.getChannel() != null ? saved.getChannel().name().toLowerCase() : "web",
                        saved.getPriority().name()
                )
        );
        eventPublisher.publishTicketCreated(event);

        log.info("Ticket created: ticketId={}, tenantId={}, ticketNumber={}, customerId={}",
                saved.getId(), tenantId, saved.getTicketNumber(), customerId);

        return saved;
    }

    // =========================================================================
    // TICKET QUERIES
    // =========================================================================

    /**
     * Returns a paginated, filtered list of tickets for a tenant.
     *
     * @param filter   filter parameters
     * @param tenantId the tenant UUID
     * @return a page of matching tickets
     */
    @Transactional(readOnly = true)
    public Page<Ticket> list(TicketFilterRequest filter, UUID tenantId) {
        Pageable pageable = PageRequest.of(
                filter.page(), filter.size(),
                Sort.by(Sort.Direction.DESC, "createdAt"));

        Specification<Ticket> spec = buildSpecification(filter, tenantId);
        return ticketRepository.findAll(spec, pageable);
    }

    /**
     * Retrieves a single ticket by its human-readable ticket number.
     *
     * @param ticketNumber the formatted ticket number (e.g., "SUP-2024-000001")
     * @param tenantId     the tenant UUID
     * @return the matching ticket
     * @throws TicketNotFoundException if the ticket does not exist for this tenant
     */
    @Transactional(readOnly = true)
    public Ticket getByTicketNumber(String ticketNumber, UUID tenantId) {
        return ticketRepository.findByTenantIdAndTicketNumber(tenantId, ticketNumber)
                .orElseThrow(() -> new TicketNotFoundException(ticketNumber));
    }

    /**
     * Returns all activities for a ticket, ordered oldest-first.
     *
     * @param ticketNumber the formatted ticket number
     * @param tenantId     the tenant UUID
     * @return list of activities
     */
    @Transactional(readOnly = true)
    public List<TicketActivity> getActivities(String ticketNumber, UUID tenantId) {
        Ticket ticket = getByTicketNumber(ticketNumber, tenantId);
        return activityRepository.findByTicketIdOrderByCreatedAtAsc(ticket.getId());
    }

    // =========================================================================
    // TICKET UPDATES
    // =========================================================================

    /**
     * Updates mutable fields of a ticket (priority, assignment, tags, custom fields).
     *
     * <p>If a status change is requested, the transition is validated against the
     * state machine before applying.
     *
     * @param ticketNumber the formatted ticket number
     * @param request      the update request (null fields are ignored)
     * @param actorId      UUID of the actor performing the update
     * @param actorType    type of the actor
     * @param tenantId     the tenant UUID
     * @return the updated ticket
     */
    public Ticket update(String ticketNumber, UpdateTicketRequest request,
                         UUID actorId, ActorType actorType, UUID tenantId) {
        Ticket ticket = getByTicketNumber(ticketNumber, tenantId);

        if (request.priority() != null) {
            ticket.setPriority(request.priority());
        }
        if (request.assignedAgentId() != null) {
            ticket.setAssignedAgentId(request.assignedAgentId());
            createActivity(ticket, actorId, actorType, ActivityType.ASSIGNMENT_CHANGE,
                    "Ticket assigned to agent: " + request.assignedAgentId(), true, tenantId);
        }
        if (request.assignedTeamId() != null) {
            ticket.setAssignedTeamId(request.assignedTeamId());
        }
        if (request.tags() != null) {
            ticket.setTags(request.tags().toArray(new String[0]));
        }
        if (request.customFields() != null) {
            ticket.setCustomFields(request.customFields());
        }

        // Status transition (if requested)
        if (request.status() != null && !request.status().equals(ticket.getStatus())) {
            TicketStatus oldStatus = ticket.getStatus();
            if (!oldStatus.canTransitionTo(request.status())) {
                throw new InvalidStatusTransitionException(oldStatus, request.status());
            }
            ticket.setStatus(request.status());
            publishStatusChangedEvent(ticket, oldStatus, actorId, actorType, null);
            createActivity(ticket, actorId, actorType, ActivityType.STATUS_CHANGE,
                    "Status changed from " + oldStatus + " to " + request.status(), false, tenantId);
        }

        Ticket saved = ticketRepository.save(ticket);
        log.info("Ticket updated: ticketId={}, tenantId={}, ticketNumber={}, actorId={}",
                saved.getId(), tenantId, saved.getTicketNumber(), actorId);
        return saved;
    }

    // =========================================================================
    // ACTIVITIES
    // =========================================================================

    /**
     * Adds a new activity (comment, note, etc.) to a ticket.
     *
     * <p>If a customer responds to a {@code PENDING_CUSTOMER_RESPONSE} ticket,
     * the ticket is automatically transitioned to {@code IN_PROGRESS}.
     *
     * @param ticketNumber the formatted ticket number
     * @param request      the activity creation request
     * @param actorId      UUID of the actor adding the activity
     * @param actorType    type of the actor
     * @param tenantId     the tenant UUID
     * @return the persisted activity
     */
    public TicketActivity addActivity(String ticketNumber, AddActivityRequest request,
                                      UUID actorId, ActorType actorType, UUID tenantId) {
        Ticket ticket = getByTicketNumber(ticketNumber, tenantId);

        TicketActivity activity = TicketActivity.builder()
                .ticketId(ticket.getId())
                .tenantId(tenantId)
                .actorId(actorId)
                .actorType(actorType)
                .activityType(request.activityType())
                .content(request.content())
                .internal(request.isInternal())
                .build();

        TicketActivity saved = activityRepository.save(activity);

        // Auto-transition: customer responds to pending-customer-response ticket
        if (actorType == ActorType.CUSTOMER
                && ticket.getStatus() == TicketStatus.PENDING_CUSTOMER_RESPONSE) {
            TicketStatus oldStatus = ticket.getStatus();
            ticket.setStatus(TicketStatus.IN_PROGRESS);
            ticketRepository.save(ticket);
            publishStatusChangedEvent(ticket, oldStatus, actorId, actorType, null);
            log.info("Auto-transitioned ticket to IN_PROGRESS on customer response: ticketId={}, tenantId={}",
                    ticket.getId(), tenantId);
        }

        // Record first agent response timestamp
        if (actorType == ActorType.AGENT && ticket.getFirstRespondedAt() == null) {
            ticket.setFirstRespondedAt(Instant.now());
            ticketRepository.save(ticket);
        }

        // Publish event
        TicketActivityAddedEvent event = new TicketActivityAddedEvent(
                UUID.randomUUID().toString(),
                tenantId.toString(),
                null,
                Instant.now(),
                new TicketActivityAddedEvent.Payload(
                        saved.getId().toString(),
                        ticket.getId().toString(),
                        ticket.getTicketNumber(),
                        ticket.getCustomerId().toString(),
                        ticket.getAssignedAgentId() != null ? ticket.getAssignedAgentId().toString() : null,
                        actorId.toString(),
                        actorType.name(),
                        request.activityType().name(),
                        truncate(request.content(), 500),
                        !request.isInternal()
                )
        );
        eventPublisher.publishActivityAdded(event);

        log.info("Activity added: activityId={}, ticketId={}, tenantId={}, activityType={}",
                saved.getId(), ticket.getId(), tenantId, request.activityType());

        return saved;
    }

    // =========================================================================
    // TICKET ACTIONS
    // =========================================================================

    /**
     * Resolves a ticket and records a resolution note.
     *
     * @param ticketNumber the formatted ticket number
     * @param request      the resolution request containing the resolution note
     * @param agentId      UUID of the agent resolving the ticket
     * @param tenantId     the tenant UUID
     * @return the updated ticket
     */
    public Ticket resolve(String ticketNumber, ResolveTicketRequest request,
                          UUID agentId, UUID tenantId) {
        Ticket ticket = getByTicketNumber(ticketNumber, tenantId);
        TicketStatus oldStatus = ticket.getStatus();

        if (!oldStatus.canTransitionTo(TicketStatus.RESOLVED)) {
            throw new InvalidStatusTransitionException(oldStatus, TicketStatus.RESOLVED);
        }

        ticket.setStatus(TicketStatus.RESOLVED);
        ticket.setResolvedAt(Instant.now());

        Ticket saved = ticketRepository.save(ticket);

        // Record resolution activity
        createActivity(saved, agentId, ActorType.AGENT, ActivityType.RESOLUTION,
                request.resolution(), false, tenantId);

        publishStatusChangedEvent(saved, oldStatus, agentId, ActorType.AGENT, request.resolution());

        log.info("Ticket resolved: ticketId={}, tenantId={}, ticketNumber={}, agentId={}",
                saved.getId(), tenantId, saved.getTicketNumber(), agentId);

        return saved;
    }

    /**
     * Reopens a resolved or closed ticket.
     *
     * @param ticketNumber the formatted ticket number
     * @param request      the reopen request containing the reason
     * @param actorId      UUID of the actor reopening the ticket
     * @param actorType    type of the actor
     * @param tenantId     the tenant UUID
     * @return the updated ticket
     */
    public Ticket reopen(String ticketNumber, ReopenTicketRequest request,
                         UUID actorId, ActorType actorType, UUID tenantId) {
        Ticket ticket = getByTicketNumber(ticketNumber, tenantId);
        TicketStatus oldStatus = ticket.getStatus();

        if (!oldStatus.canTransitionTo(TicketStatus.REOPENED)) {
            throw new InvalidStatusTransitionException(oldStatus, TicketStatus.REOPENED);
        }

        ticket.setStatus(TicketStatus.REOPENED);
        ticket.setResolvedAt(null);
        ticket.setClosedAt(null);

        Ticket saved = ticketRepository.save(ticket);

        createActivity(saved, actorId, actorType, ActivityType.SYSTEM,
                "Ticket reopened: " + request.reason(), false, tenantId);

        publishStatusChangedEvent(saved, oldStatus, actorId, actorType, null);

        log.info("Ticket reopened: ticketId={}, tenantId={}, ticketNumber={}, actorId={}",
                saved.getId(), tenantId, saved.getTicketNumber(), actorId);

        return saved;
    }

    /**
     * Escalates a ticket.
     *
     * @param ticketNumber the formatted ticket number
     * @param agentId      UUID of the agent escalating the ticket
     * @param tenantId     the tenant UUID
     * @return the updated ticket
     */
    public Ticket escalate(String ticketNumber, UUID agentId, UUID tenantId) {
        Ticket ticket = getByTicketNumber(ticketNumber, tenantId);
        TicketStatus oldStatus = ticket.getStatus();

        if (!oldStatus.canTransitionTo(TicketStatus.ESCALATED)) {
            throw new InvalidStatusTransitionException(oldStatus, TicketStatus.ESCALATED);
        }

        ticket.setStatus(TicketStatus.ESCALATED);
        Ticket saved = ticketRepository.save(ticket);

        createActivity(saved, agentId, ActorType.AGENT, ActivityType.STATUS_CHANGE,
                "Ticket escalated", false, tenantId);

        publishStatusChangedEvent(saved, oldStatus, agentId, ActorType.AGENT, null);

        log.info("Ticket escalated: ticketId={}, tenantId={}, ticketNumber={}, agentId={}",
                saved.getId(), tenantId, saved.getTicketNumber(), agentId);

        return saved;
    }

    // =========================================================================
    // AI SENTIMENT UPDATE
    // =========================================================================

    /**
     * Updates sentiment fields on a ticket after AI analysis completes.
     *
     * <p>Called by {@link in.supporthub.ticket.event.AiResultEventListener} when a
     * {@code ai.sentiment-analysis-completed} event is received.
     *
     * @param ticketId UUID string of the ticket to update
     * @param label    the computed sentiment label
     * @param score    the normalised sentiment score (-1.0 to 1.0)
     */
    public void updateSentiment(String ticketId, SentimentLabel label, float score) {
        ticketRepository.findById(UUID.fromString(ticketId)).ifPresent(ticket -> {
            ticket.setSentimentScore(score);
            ticket.setSentimentLabel(label);
            ticket.setSentimentUpdatedAt(Instant.now());
            ticketRepository.save(ticket);
            log.info("Sentiment updated: ticketId={}, tenantId={}, label={}, score={}",
                    ticketId, ticket.getTenantId(), label, score);
        });
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    private void createSystemActivity(Ticket ticket, String message, UUID tenantId) {
        // Use a system UUID as actor — no real user involved
        UUID systemActorId = new UUID(0L, 0L);
        createActivity(ticket, systemActorId, ActorType.SYSTEM, ActivityType.SYSTEM,
                message, true, tenantId);
    }

    private void createActivity(Ticket ticket, UUID actorId, ActorType actorType,
                                 ActivityType activityType, String content,
                                 boolean internal, UUID tenantId) {
        TicketActivity activity = TicketActivity.builder()
                .ticketId(ticket.getId())
                .tenantId(tenantId)
                .actorId(actorId)
                .actorType(actorType)
                .activityType(activityType)
                .content(content)
                .internal(internal)
                .build();
        activityRepository.save(activity);
    }

    private void publishStatusChangedEvent(Ticket ticket, TicketStatus oldStatus,
                                            UUID actorId, ActorType actorType,
                                            String resolutionNote) {
        TicketStatusChangedEvent event = new TicketStatusChangedEvent(
                UUID.randomUUID().toString(),
                ticket.getTenantId().toString(),
                null,
                Instant.now(),
                new TicketStatusChangedEvent.Payload(
                        ticket.getId().toString(),
                        ticket.getTicketNumber(),
                        ticket.getCustomerId().toString(),
                        ticket.getAssignedAgentId() != null ? ticket.getAssignedAgentId().toString() : null,
                        oldStatus.name(),
                        ticket.getStatus().name(),
                        actorId.toString(),
                        actorType.name(),
                        resolutionNote
                )
        );
        eventPublisher.publishStatusChanged(event);
    }

    private Specification<Ticket> buildSpecification(TicketFilterRequest filter, UUID tenantId) {
        return (root, query, cb) -> {
            var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();

            // Always filter by tenant
            predicates.add(cb.equal(root.get("tenantId"), tenantId));

            if (filter.status() != null && !filter.status().isEmpty()) {
                predicates.add(root.get("status").in(filter.status()));
            }
            if (filter.categoryId() != null) {
                predicates.add(cb.equal(root.get("categoryId"), filter.categoryId()));
            }
            if (filter.priority() != null) {
                predicates.add(cb.equal(root.get("priority"), filter.priority()));
            }
            if (filter.assignedAgentId() != null) {
                predicates.add(cb.equal(root.get("assignedAgentId"), filter.assignedAgentId()));
            }
            if (filter.customerId() != null) {
                predicates.add(cb.equal(root.get("customerId"), filter.customerId()));
            }
            if (filter.sentimentLabel() != null) {
                predicates.add(cb.equal(root.get("sentimentLabel"), filter.sentimentLabel()));
            }
            if (filter.dateFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), filter.dateFrom()));
            }
            if (filter.dateTo() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), filter.dateTo()));
            }

            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }

    private static String truncate(String text, int maxLength) {
        if (text == null) {
            return null;
        }
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }
}
