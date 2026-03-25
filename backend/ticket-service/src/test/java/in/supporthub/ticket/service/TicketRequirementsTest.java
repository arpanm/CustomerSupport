package in.supporthub.ticket.service;

import in.supporthub.ticket.domain.ActorType;
import in.supporthub.ticket.domain.Priority;
import in.supporthub.ticket.domain.SentimentLabel;
import in.supporthub.ticket.domain.Ticket;
import in.supporthub.ticket.domain.TicketActivity;
import in.supporthub.ticket.domain.TicketCategory;
import in.supporthub.ticket.domain.TicketStatus;
import in.supporthub.ticket.dto.CreateTicketRequest;
import in.supporthub.ticket.dto.ReopenTicketRequest;
import in.supporthub.ticket.dto.ResolveTicketRequest;
import in.supporthub.ticket.dto.TicketFilterRequest;
import in.supporthub.ticket.dto.UpdateTicketRequest;
import in.supporthub.ticket.exception.InvalidStatusTransitionException;
import in.supporthub.ticket.fixtures.TicketFixtures;
import in.supporthub.ticket.repository.SlaPolicyRepository;
import in.supporthub.ticket.repository.TicketActivityRepository;
import in.supporthub.ticket.repository.TicketCategoryRepository;
import in.supporthub.ticket.repository.TicketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Requirements-oriented unit tests for {@link TicketService}.
 *
 * <p>Each test method is keyed to a REQ-* identifier from REQUIREMENT.md and documents
 * the expected behaviour. Tests that target behaviour not yet implemented in the service
 * are marked {@link Disabled} with the reason and REQ-* identifier.
 *
 * <p>All external dependencies are mocked; no Spring context is loaded.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TicketService — Requirements Tests")
class TicketRequirementsTest {

    // -------------------------------------------------------------------------
    // Mocks
    // -------------------------------------------------------------------------

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private TicketActivityRepository activityRepository;

    @Mock
    private TicketCategoryRepository categoryRepository;

    @Mock
    private SlaPolicyRepository slaPolicyRepository;

    @Mock
    private TicketNumberGenerator ticketNumberGenerator;

    @Mock
    private SlaEngine slaEngine;

    @Mock
    private TicketEventPublisher eventPublisher;

    @InjectMocks
    private TicketService ticketService;

    // -------------------------------------------------------------------------
    // Shared constants (sourced from TicketFixtures to avoid duplication)
    // -------------------------------------------------------------------------

    private static final UUID TENANT_ID   = TicketFixtures.DEFAULT_TENANT_ID;
    private static final UUID CUSTOMER_ID = TicketFixtures.DEFAULT_CUSTOMER_ID;
    private static final UUID CATEGORY_ID = TicketFixtures.DEFAULT_CATEGORY_ID;
    private static final UUID AGENT_ID    = UUID.fromString("40000000-0000-0000-0000-000000000004");

    /** A valid ticket title (10–200 chars). */
    private static final String VALID_TITLE = "Order did not arrive";

    /** A valid ticket description (≥ 20 chars). */
    private static final String VALID_DESCRIPTION =
            "I placed order #12345 five days ago and it still has not arrived.";

    private TicketCategory activeCategory;

    @BeforeEach
    void setUp() {
        activeCategory = TicketCategory.builder()
                .id(CATEGORY_ID)
                .tenantId(TENANT_ID)
                .name("Order Issues")
                .slug("order-issues")
                .slaFirstResponseHours(4)
                .slaResolutionHours(24)
                .defaultPriority(Priority.MEDIUM)
                .active(true)
                .build();
    }

    // =========================================================================
    // REQ-CUI-CREATE-04 — Title and description length constraints
    // =========================================================================

    /**
     * These constraints are declared as Bean Validation annotations on {@link CreateTicketRequest}
     * (@Size) and are enforced by the controller layer before the service is called.
     * The service itself does not re-validate the lengths, so the tests below document
     * the contract at the DTO level and confirm that a request that satisfies the
     * constraints reaches the service and succeeds.
     */
    @Nested
    @DisplayName("REQ-CUI-CREATE-04: title and description length constraints")
    class TitleDescriptionLengthTests {

        /** Common mock stubs used whenever a successful creation flow is needed. */
        private void stubSuccessfulCreation(Ticket savedTicket) {
            when(categoryRepository.findById(CATEGORY_ID))
                    .thenReturn(Optional.of(activeCategory));
            when(slaPolicyRepository.findByTenantIdAndCategoryIdAndActive(TENANT_ID, CATEGORY_ID, true))
                    .thenReturn(List.of());
            when(ticketNumberGenerator.generate(TENANT_ID, "SUP"))
                    .thenReturn("SUP-2024-000001");
            when(slaEngine.compute(any(Ticket.class), eq(4), eq(24)))
                    .thenReturn(new SlaDeadlines(
                            Instant.now().plus(4, ChronoUnit.HOURS),
                            Instant.now().plus(24, ChronoUnit.HOURS)));
            when(ticketRepository.save(any(Ticket.class))).thenReturn(savedTicket);
            when(activityRepository.save(any(TicketActivity.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
        }

        @Test
        @DisplayName("REQ-CUI-CREATE-04: ticket creation succeeds when title meets minimum length (10 chars)")
        void shouldCreateTicketWhenTitleMeetsMinimumLength() {
            // ARRANGE — title is exactly 10 characters
            String minLengthTitle = "1234567890"; // 10 chars
            CreateTicketRequest request = new CreateTicketRequest(
                    minLengthTitle, VALID_DESCRIPTION, CATEGORY_ID, null, null, null, null);

            Ticket savedTicket = TicketFixtures.openTicket(TENANT_ID).toBuilder()
                    .title(minLengthTitle)
                    .build();
            stubSuccessfulCreation(savedTicket);

            // ACT
            Ticket result = ticketService.create(request, CUSTOMER_ID, TENANT_ID);

            // ASSERT — service accepted the request and saved the ticket
            assertThat(result).isNotNull();
            assertThat(result.getTitle()).isEqualTo(minLengthTitle);
        }

        @Test
        @DisplayName("REQ-CUI-CREATE-04: ticket creation succeeds when title is at maximum length (200 chars)")
        void shouldCreateTicketWhenTitleIsAtMaximumLength() {
            // ARRANGE — title is exactly 200 characters
            String maxLengthTitle = "A".repeat(200);
            CreateTicketRequest request = new CreateTicketRequest(
                    maxLengthTitle, VALID_DESCRIPTION, CATEGORY_ID, null, null, null, null);

            Ticket savedTicket = TicketFixtures.openTicket(TENANT_ID).toBuilder()
                    .title(maxLengthTitle)
                    .build();
            stubSuccessfulCreation(savedTicket);

            // ACT
            Ticket result = ticketService.create(request, CUSTOMER_ID, TENANT_ID);

            // ASSERT
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("REQ-CUI-CREATE-04: ticket creation succeeds when description meets minimum length (20 chars)")
        void shouldCreateTicketWhenDescriptionMeetsMinimumLength() {
            // ARRANGE — description is exactly 20 characters
            String minLengthDescription = "12345678901234567890"; // 20 chars
            CreateTicketRequest request = new CreateTicketRequest(
                    VALID_TITLE, minLengthDescription, CATEGORY_ID, null, null, null, null);

            Ticket savedTicket = TicketFixtures.openTicket(TENANT_ID).toBuilder()
                    .description(minLengthDescription)
                    .build();
            stubSuccessfulCreation(savedTicket);

            // ACT
            Ticket result = ticketService.create(request, CUSTOMER_ID, TENANT_ID);

            // ASSERT
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("REQ-CUI-CREATE-04: CreateTicketRequest DTO declares @Size(min=10, max=200) on title")
        void dtoShouldDeclareCorrectTitleSizeConstraint() throws NoSuchFieldException {
            // This documents that the @Size validation annotation is in place on the DTO.
            // Controller-layer validation (Spring @Valid) enforces these at the HTTP boundary.
            var titleField = CreateTicketRequest.class.getDeclaredField("title");
            var sizeAnnotations = titleField.getAnnotationsByType(jakarta.validation.constraints.Size.class);

            assertThat(sizeAnnotations)
                    .as("@Size annotation must be present on CreateTicketRequest.title")
                    .isNotEmpty();

            jakarta.validation.constraints.Size size = sizeAnnotations[0];
            assertThat(size.min())
                    .as("Minimum title length must be 10")
                    .isEqualTo(10);
            assertThat(size.max())
                    .as("Maximum title length must be 200")
                    .isEqualTo(200);
        }

        @Test
        @DisplayName("REQ-CUI-CREATE-04: CreateTicketRequest DTO declares @Size(min=20) on description")
        void dtoShouldDeclareCorrectDescriptionSizeConstraint() throws NoSuchFieldException {
            var descField = CreateTicketRequest.class.getDeclaredField("description");
            var sizeAnnotations = descField.getAnnotationsByType(jakarta.validation.constraints.Size.class);

            assertThat(sizeAnnotations)
                    .as("@Size annotation must be present on CreateTicketRequest.description")
                    .isNotEmpty();

            jakarta.validation.constraints.Size size = sizeAnnotations[0];
            assertThat(size.min())
                    .as("Minimum description length must be 20")
                    .isEqualTo(20);
        }
    }

    // =========================================================================
    // REQ-CUI-CREATE-08 — Duplicate ticket detection
    // =========================================================================

    @Nested
    @DisplayName("REQ-CUI-CREATE-08: duplicate ticket detection")
    class DuplicateDetectionTests {

        @Test
        @Disabled("REQ-CUI-CREATE-08: not yet implemented — TicketService.create() does not query for " +
                  "existing open tickets with the same (customerId, orderId, subCategoryId) combination. " +
                  "A findByTenantIdAndCustomerIdAndOrderIdAndSubCategoryIdAndStatusIn() repository method " +
                  "and a DuplicateTicketException need to be added, and create() must call the check before " +
                  "persisting the new ticket.")
        @DisplayName("REQ-CUI-CREATE-08: should reject creation when an open ticket already exists for same customer+order+sub-category")
        void shouldRejectCreationWhenDuplicateOpenTicketExists() {
            UUID orderId = UUID.randomUUID();
            UUID subCategoryId = UUID.randomUUID();

            // An existing OPEN ticket for the same customer, order, and sub-category
            Ticket existingTicket = TicketFixtures.openTicket(TENANT_ID).toBuilder()
                    .customerId(CUSTOMER_ID)
                    .orderId(orderId)
                    .subCategoryId(subCategoryId)
                    .status(TicketStatus.OPEN)
                    .build();

            CreateTicketRequest request = new CreateTicketRequest(
                    VALID_TITLE, VALID_DESCRIPTION, CATEGORY_ID, subCategoryId, orderId, null, null);

            // When the service checks for duplicate open tickets it should find the existing one.
            // (Stub this once the repository method is added.)

            // ACT + ASSERT — expect a duplicate / conflict exception
            assertThatThrownBy(() -> ticketService.create(request, CUSTOMER_ID, TENANT_ID))
                    .isInstanceOf(RuntimeException.class)
                    .satisfies(ex ->
                            assertThat(ex.getClass().getSimpleName())
                                    .contains("Duplicate"));
        }

        @Test
        @Disabled("REQ-CUI-CREATE-08: not yet implemented — no duplicate-check logic exists in create(). " +
                  "Once implemented this test should verify that a new ticket is created when the only " +
                  "existing ticket for the same order+sub-category is in a terminal state (RESOLVED/CLOSED).")
        @DisplayName("REQ-CUI-CREATE-08: should allow creation when existing ticket for same order+sub-category is already resolved")
        void shouldAllowCreationWhenExistingTicketIsResolved() {
            UUID orderId = UUID.randomUUID();
            UUID subCategoryId = UUID.randomUUID();

            CreateTicketRequest request = new CreateTicketRequest(
                    VALID_TITLE, VALID_DESCRIPTION, CATEGORY_ID, subCategoryId, orderId, null, null);

            // No open duplicates found — only a resolved ticket exists, so creation should proceed.
            when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.of(activeCategory));
            when(slaPolicyRepository.findByTenantIdAndCategoryIdAndActive(TENANT_ID, CATEGORY_ID, true))
                    .thenReturn(List.of());
            when(ticketNumberGenerator.generate(TENANT_ID, "SUP")).thenReturn("SUP-2024-000002");
            when(slaEngine.compute(any(Ticket.class), eq(4), eq(24)))
                    .thenReturn(new SlaDeadlines(
                            Instant.now().plus(4, ChronoUnit.HOURS),
                            Instant.now().plus(24, ChronoUnit.HOURS)));
            Ticket saved = TicketFixtures.openTicket(TENANT_ID);
            when(ticketRepository.save(any(Ticket.class))).thenReturn(saved);
            when(activityRepository.save(any(TicketActivity.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            Ticket result = ticketService.create(request, CUSTOMER_ID, TENANT_ID);

            assertThat(result).isNotNull();
            assertThat(result.getStatus()).isEqualTo(TicketStatus.OPEN);
        }
    }

    // =========================================================================
    // REQ-CUI-DETAIL-06 — Status transition rules: resolve, reopen, reopen limit
    // =========================================================================

    @Nested
    @DisplayName("REQ-CUI-DETAIL-06: status transition rules for resolve and reopen")
    class StatusTransitionTests {

        // ---- Resolve --------------------------------------------------------

        @Test
        @DisplayName("REQ-CUI-DETAIL-06: should resolve ticket when status is IN_PROGRESS")
        void shouldResolveTicketFromInProgress() {
            // ARRANGE
            Ticket ticket = TicketFixtures.ticketWithStatus(TENANT_ID, TicketStatus.IN_PROGRESS);
            when(ticketRepository.findByTenantIdAndTicketNumber(TENANT_ID, ticket.getTicketNumber()))
                    .thenReturn(Optional.of(ticket));
            when(ticketRepository.save(any(Ticket.class))).thenAnswer(inv -> inv.getArgument(0));
            when(activityRepository.save(any(TicketActivity.class))).thenAnswer(inv -> inv.getArgument(0));

            ResolveTicketRequest request = new ResolveTicketRequest("Issue resolved — replacement dispatched.");

            // ACT
            Ticket result = ticketService.resolve(ticket.getTicketNumber(), request, AGENT_ID, TENANT_ID);

            // ASSERT
            assertThat(result.getStatus()).isEqualTo(TicketStatus.RESOLVED);
            assertThat(result.getResolvedAt()).isNotNull();
        }

        @Test
        @DisplayName("REQ-CUI-DETAIL-06: should resolve ticket when status is PENDING_CUSTOMER_RESPONSE")
        void shouldResolveTicketFromPendingCustomerResponse() {
            // ARRANGE
            Ticket ticket = TicketFixtures.ticketWithStatus(TENANT_ID, TicketStatus.PENDING_CUSTOMER_RESPONSE);
            when(ticketRepository.findByTenantIdAndTicketNumber(TENANT_ID, ticket.getTicketNumber()))
                    .thenReturn(Optional.of(ticket));
            when(ticketRepository.save(any(Ticket.class))).thenAnswer(inv -> inv.getArgument(0));
            when(activityRepository.save(any(TicketActivity.class))).thenAnswer(inv -> inv.getArgument(0));

            ResolveTicketRequest request = new ResolveTicketRequest("Resolved after awaiting customer input.");

            // ACT
            Ticket result = ticketService.resolve(ticket.getTicketNumber(), request, AGENT_ID, TENANT_ID);

            // ASSERT — PENDING_CUSTOMER_RESPONSE → RESOLVED is a valid transition per REQ-CUI-DETAIL-06
            // (agent may resolve the ticket while awaiting customer response)
            assertThat(result.getStatus()).isEqualTo(TicketStatus.RESOLVED);
        }

        @Test
        @DisplayName("REQ-CUI-DETAIL-06: should reject resolving ticket when status is OPEN")
        void shouldRejectResolvingFromOpen() {
            // ARRANGE — OPEN → RESOLVED is not a valid transition in the state machine
            Ticket ticket = TicketFixtures.ticketWithStatus(TENANT_ID, TicketStatus.OPEN);
            when(ticketRepository.findByTenantIdAndTicketNumber(TENANT_ID, ticket.getTicketNumber()))
                    .thenReturn(Optional.of(ticket));

            ResolveTicketRequest request = new ResolveTicketRequest("Attempting resolve from OPEN — must fail.");

            // ACT + ASSERT
            assertThatThrownBy(() ->
                    ticketService.resolve(ticket.getTicketNumber(), request, AGENT_ID, TENANT_ID))
                    .isInstanceOf(InvalidStatusTransitionException.class)
                    .hasMessageContaining("OPEN")
                    .hasMessageContaining("RESOLVED");
        }

        @Test
        @DisplayName("REQ-CUI-DETAIL-06: should reject resolving ticket when status is CLOSED")
        void shouldRejectResolvingFromClosed() {
            // ARRANGE — CLOSED → RESOLVED is not a valid transition
            Ticket ticket = TicketFixtures.ticketWithStatus(TENANT_ID, TicketStatus.CLOSED);
            when(ticketRepository.findByTenantIdAndTicketNumber(TENANT_ID, ticket.getTicketNumber()))
                    .thenReturn(Optional.of(ticket));

            ResolveTicketRequest request = new ResolveTicketRequest("Attempting resolve from CLOSED — must fail.");

            // ACT + ASSERT
            assertThatThrownBy(() ->
                    ticketService.resolve(ticket.getTicketNumber(), request, AGENT_ID, TENANT_ID))
                    .isInstanceOf(InvalidStatusTransitionException.class)
                    .hasMessageContaining("CLOSED")
                    .hasMessageContaining("RESOLVED");
        }

        // ---- Reopen ---------------------------------------------------------

        @Test
        @DisplayName("REQ-CUI-DETAIL-06: should reopen ticket when status is RESOLVED")
        void shouldReopenTicketFromResolved() {
            // ARRANGE
            Ticket ticket = TicketFixtures.resolvedTicket(TENANT_ID);
            when(ticketRepository.findByTenantIdAndTicketNumber(TENANT_ID, ticket.getTicketNumber()))
                    .thenReturn(Optional.of(ticket));
            when(ticketRepository.save(any(Ticket.class))).thenAnswer(inv -> inv.getArgument(0));
            when(activityRepository.save(any(TicketActivity.class))).thenAnswer(inv -> inv.getArgument(0));

            ReopenTicketRequest request = new ReopenTicketRequest("Issue recurred after resolution.");

            // ACT
            Ticket result = ticketService.reopen(ticket.getTicketNumber(), request, CUSTOMER_ID,
                    ActorType.CUSTOMER, TENANT_ID);

            // ASSERT
            assertThat(result.getStatus()).isEqualTo(TicketStatus.REOPENED);
            assertThat(result.getResolvedAt()).isNull();
        }

        @Test
        @DisplayName("REQ-CUI-DETAIL-06: should reopen ticket when status is CLOSED")
        void shouldReopenTicketFromClosed() {
            // ARRANGE
            Ticket ticket = TicketFixtures.ticketWithStatus(TENANT_ID, TicketStatus.CLOSED);
            when(ticketRepository.findByTenantIdAndTicketNumber(TENANT_ID, ticket.getTicketNumber()))
                    .thenReturn(Optional.of(ticket));
            when(ticketRepository.save(any(Ticket.class))).thenAnswer(inv -> inv.getArgument(0));
            when(activityRepository.save(any(TicketActivity.class))).thenAnswer(inv -> inv.getArgument(0));

            ReopenTicketRequest request = new ReopenTicketRequest("Customer re-raised the problem.");

            // ACT
            Ticket result = ticketService.reopen(ticket.getTicketNumber(), request, CUSTOMER_ID,
                    ActorType.CUSTOMER, TENANT_ID);

            // ASSERT
            assertThat(result.getStatus()).isEqualTo(TicketStatus.REOPENED);
        }

        @Test
        @DisplayName("REQ-CUI-DETAIL-06: should reject reopening ticket when status is IN_PROGRESS")
        void shouldRejectReopeningFromInProgress() {
            // ARRANGE — IN_PROGRESS → REOPENED is not a valid transition
            Ticket ticket = TicketFixtures.ticketWithStatus(TENANT_ID, TicketStatus.IN_PROGRESS);
            when(ticketRepository.findByTenantIdAndTicketNumber(TENANT_ID, ticket.getTicketNumber()))
                    .thenReturn(Optional.of(ticket));

            ReopenTicketRequest request = new ReopenTicketRequest("Attempting reopen from IN_PROGRESS — must fail.");

            // ACT + ASSERT
            assertThatThrownBy(() ->
                    ticketService.reopen(ticket.getTicketNumber(), request, CUSTOMER_ID,
                            ActorType.CUSTOMER, TENANT_ID))
                    .isInstanceOf(InvalidStatusTransitionException.class)
                    .hasMessageContaining("IN_PROGRESS")
                    .hasMessageContaining("REOPENED");
        }

        @Test
        @DisplayName("REQ-CUI-DETAIL-06: should reject reopening ticket when status is OPEN")
        void shouldRejectReopeningFromOpen() {
            // ARRANGE — OPEN → REOPENED is not a valid transition
            Ticket ticket = TicketFixtures.ticketWithStatus(TENANT_ID, TicketStatus.OPEN);
            when(ticketRepository.findByTenantIdAndTicketNumber(TENANT_ID, ticket.getTicketNumber()))
                    .thenReturn(Optional.of(ticket));

            ReopenTicketRequest request = new ReopenTicketRequest("Attempting reopen from OPEN — must fail.");

            // ACT + ASSERT
            assertThatThrownBy(() ->
                    ticketService.reopen(ticket.getTicketNumber(), request, CUSTOMER_ID,
                            ActorType.CUSTOMER, TENANT_ID))
                    .isInstanceOf(InvalidStatusTransitionException.class);
        }

        // ---- Reopen limit ---------------------------------------------------

        @Test
        @Disabled("REQ-CUI-DETAIL-06: not yet implemented — the Ticket entity has no reopenCount field " +
                  "and TicketService.reopen() does not enforce the maximum-2-reopens rule. " +
                  "A reopenCount column must be added to the tickets table, incremented in reopen(), " +
                  "and a ReopenLimitExceededException thrown when count >= 2.")
        @DisplayName("REQ-CUI-DETAIL-06: should reject third reopen attempt (max 2 reopens enforced)")
        void shouldRejectThirdReopenWhenMaxReopensExceeded() {
            // ARRANGE — simulate a ticket that has already been reopened twice
            Ticket ticket = TicketFixtures.resolvedTicket(TENANT_ID).toBuilder()
                    // reopenCount = 2 — once this field exists on the entity
                    .build();

            when(ticketRepository.findByTenantIdAndTicketNumber(TENANT_ID, ticket.getTicketNumber()))
                    .thenReturn(Optional.of(ticket));

            ReopenTicketRequest request = new ReopenTicketRequest("Third reopen attempt — must be rejected.");

            // ACT + ASSERT — expect a domain exception signalling the limit is exceeded
            assertThatThrownBy(() ->
                    ticketService.reopen(ticket.getTicketNumber(), request, CUSTOMER_ID,
                            ActorType.CUSTOMER, TENANT_ID))
                    .isInstanceOf(RuntimeException.class)
                    .satisfies(ex ->
                            assertThat(ex.getClass().getSimpleName())
                                    .containsIgnoringCase("Reopen"));
        }
    }

    // =========================================================================
    // REQ-AI-SENT-06 — VERY_NEGATIVE sentiment auto-escalates priority to HIGH
    // =========================================================================

    @Nested
    @DisplayName("REQ-AI-SENT-06: VERY_NEGATIVE sentiment auto-escalates priority")
    class SentimentEscalationTests {

        @Test
        @Disabled("REQ-AI-SENT-06: not yet implemented — updateSentiment() in TicketService persists the " +
                  "sentiment label and score but does NOT auto-escalate priority to HIGH when the label is " +
                  "VERY_NEGATIVE. The method needs an additional check: " +
                  "if (label == SentimentLabel.VERY_NEGATIVE) { ticket.setPriority(Priority.HIGH); } " +
                  "before saving.")
        @DisplayName("REQ-AI-SENT-06: should auto-escalate priority to HIGH when sentiment is VERY_NEGATIVE")
        void shouldEscalatePriorityToHighOnVeryNegativeSentiment() {
            // ARRANGE — ticket currently has MEDIUM priority
            Ticket ticket = TicketFixtures.openTicket(TENANT_ID).toBuilder()
                    .priority(Priority.MEDIUM)
                    .build();
            when(ticketRepository.findById(ticket.getId())).thenReturn(Optional.of(ticket));
            when(ticketRepository.save(any(Ticket.class))).thenAnswer(inv -> inv.getArgument(0));

            // ACT — AI analysis completed with VERY_NEGATIVE result
            ticketService.updateSentiment(ticket.getId().toString(), SentimentLabel.VERY_NEGATIVE, -0.85f);

            // ASSERT — priority must have been escalated to HIGH
            ArgumentCaptor<Ticket> savedCaptor = ArgumentCaptor.forClass(Ticket.class);
            verify(ticketRepository).save(savedCaptor.capture());

            Ticket saved = savedCaptor.getValue();
            assertThat(saved.getSentimentLabel()).isEqualTo(SentimentLabel.VERY_NEGATIVE);
            assertThat(saved.getPriority())
                    .as("Priority must be auto-escalated to HIGH for VERY_NEGATIVE sentiment")
                    .isEqualTo(Priority.HIGH);
        }

        @Test
        @DisplayName("REQ-AI-SENT-06: should NOT change priority when sentiment is NEGATIVE (not VERY_NEGATIVE)")
        void shouldNotChangePriorityForNegativeSentiment() {
            // ARRANGE — NEGATIVE sentiment must NOT trigger auto-escalation
            Ticket ticket = TicketFixtures.openTicket(TENANT_ID).toBuilder()
                    .priority(Priority.MEDIUM)
                    .build();
            when(ticketRepository.findById(ticket.getId())).thenReturn(Optional.of(ticket));
            when(ticketRepository.save(any(Ticket.class))).thenAnswer(inv -> inv.getArgument(0));

            // ACT
            ticketService.updateSentiment(ticket.getId().toString(), SentimentLabel.NEGATIVE, -0.4f);

            // ASSERT — sentiment is stored but priority remains MEDIUM
            ArgumentCaptor<Ticket> savedCaptor = ArgumentCaptor.forClass(Ticket.class);
            verify(ticketRepository).save(savedCaptor.capture());

            Ticket saved = savedCaptor.getValue();
            assertThat(saved.getSentimentLabel()).isEqualTo(SentimentLabel.NEGATIVE);
            // Priority should be unchanged (MEDIUM); there is currently no escalation logic,
            // so this test documents that non-VERY_NEGATIVE labels must never trigger escalation.
            assertThat(saved.getPriority())
                    .as("Priority must NOT be escalated for plain NEGATIVE sentiment")
                    .isEqualTo(Priority.MEDIUM);
        }

        @Test
        @DisplayName("REQ-AI-SENT-06: should store sentiment fields correctly for any label")
        void shouldStoreSentimentFieldsForAnyLabel() {
            // ARRANGE
            Ticket ticket = TicketFixtures.openTicket(TENANT_ID);
            when(ticketRepository.findById(ticket.getId())).thenReturn(Optional.of(ticket));
            when(ticketRepository.save(any(Ticket.class))).thenAnswer(inv -> inv.getArgument(0));

            // ACT
            ticketService.updateSentiment(ticket.getId().toString(), SentimentLabel.NEUTRAL, 0.05f);

            // ASSERT
            ArgumentCaptor<Ticket> savedCaptor = ArgumentCaptor.forClass(Ticket.class);
            verify(ticketRepository).save(savedCaptor.capture());

            Ticket saved = savedCaptor.getValue();
            assertThat(saved.getSentimentLabel()).isEqualTo(SentimentLabel.NEUTRAL);
            assertThat(saved.getSentimentScore()).isEqualTo(0.05f);
            assertThat(saved.getSentimentUpdatedAt()).isNotNull();
        }
    }

    // =========================================================================
    // REQ-CUI-LIST-01 — List tickets in reverse chronological order
    // =========================================================================

    @Nested
    @DisplayName("REQ-CUI-LIST-01: list tickets in reverse chronological order")
    class ListOrderTests {

        @Test
        @DisplayName("REQ-CUI-LIST-01: list() should request DESC sort by createdAt from the repository")
        void shouldRequestDescendingCreatedAtSort() {
            // ARRANGE
            TicketFilterRequest filter = new TicketFilterRequest(
                    null, null, null, null, null, null, null, null, 0, 25);

            Ticket newerTicket = TicketFixtures.openTicket(TENANT_ID).toBuilder()
                    .createdAt(Instant.now())
                    .build();
            Ticket olderTicket = TicketFixtures.openTicket(TENANT_ID).toBuilder()
                    .createdAt(Instant.now().minus(1, ChronoUnit.DAYS))
                    .build();

            // Repository returns page already sorted DESC (as it would with the Pageable)
            Page<Ticket> page = new PageImpl<>(List.of(newerTicket, olderTicket));
            when(ticketRepository.findAll(any(Specification.class), any(Pageable.class)))
                    .thenReturn(page);

            // ACT
            Page<Ticket> result = ticketService.list(filter, TENANT_ID);

            // ASSERT — capture the Pageable to verify sort direction
            ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
            verify(ticketRepository).findAll(any(Specification.class), pageableCaptor.capture());

            Pageable pageable = pageableCaptor.getValue();
            assertThat(pageable.getSort().isSorted())
                    .as("Sort must be specified")
                    .isTrue();
            assertThat(pageable.getSort().getOrderFor("createdAt"))
                    .as("Sort order for createdAt must exist")
                    .isNotNull()
                    .satisfies(order ->
                            assertThat(order.getDirection().isDescending())
                                    .as("createdAt must be sorted DESCENDING (newest first)")
                                    .isTrue());
        }

        @Test
        @DisplayName("REQ-CUI-LIST-01: list() results are ordered newest-first when repository returns them that way")
        void shouldReturnResultsNewestFirst() {
            // ARRANGE
            Instant now = Instant.now();
            Ticket newerTicket = TicketFixtures.openTicket(TENANT_ID).toBuilder()
                    .ticketNumber("TEST-2024-000002")
                    .createdAt(now)
                    .build();
            Ticket olderTicket = TicketFixtures.openTicket(TENANT_ID).toBuilder()
                    .ticketNumber("TEST-2024-000001")
                    .createdAt(now.minus(2, ChronoUnit.DAYS))
                    .build();

            TicketFilterRequest filter = new TicketFilterRequest(
                    null, null, null, null, null, null, null, null, 0, 25);
            Page<Ticket> sortedPage = new PageImpl<>(List.of(newerTicket, olderTicket));
            when(ticketRepository.findAll(any(Specification.class), any(Pageable.class)))
                    .thenReturn(sortedPage);

            // ACT
            Page<Ticket> result = ticketService.list(filter, TENANT_ID);

            // ASSERT — first result must be the newer ticket
            List<Ticket> tickets = result.getContent();
            assertThat(tickets).hasSize(2);
            assertThat(tickets.get(0).getCreatedAt())
                    .as("First ticket in list must have a later createdAt than the second")
                    .isAfterOrEqualTo(tickets.get(1).getCreatedAt());
        }

        @Test
        @DisplayName("REQ-CUI-LIST-01: list() uses the page and size from TicketFilterRequest")
        void shouldPassThroughPageAndSizeToRepository() {
            // ARRANGE
            int requestedPage = 2;
            int requestedSize = 10;
            TicketFilterRequest filter = new TicketFilterRequest(
                    null, null, null, null, null, null, null, null,
                    requestedPage, requestedSize);
            when(ticketRepository.findAll(any(Specification.class), any(Pageable.class)))
                    .thenReturn(Page.empty());

            // ACT
            ticketService.list(filter, TENANT_ID);

            // ASSERT
            ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
            verify(ticketRepository).findAll(any(Specification.class), pageableCaptor.capture());

            Pageable pageable = pageableCaptor.getValue();
            assertThat(pageable.getPageNumber()).isEqualTo(requestedPage);
            assertThat(pageable.getPageSize()).isEqualTo(requestedSize);
        }
    }

    // =========================================================================
    // REQ-CUI-AUTH-05 — Ticket creation requires authentication (tenantId non-null)
    // =========================================================================

    @Nested
    @DisplayName("REQ-CUI-AUTH-05: ticket creation requires authentication (tenantId must not be null)")
    class AuthenticationRequirementTests {

        @Test
        @DisplayName("REQ-CUI-AUTH-05: should succeed when valid tenantId is provided (authenticated context)")
        void shouldCreateTicketWhenTenantIdIsProvided() {
            // ARRANGE
            CreateTicketRequest request = new CreateTicketRequest(
                    VALID_TITLE, VALID_DESCRIPTION, CATEGORY_ID, null, null, null, null);

            when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.of(activeCategory));
            when(slaPolicyRepository.findByTenantIdAndCategoryIdAndActive(TENANT_ID, CATEGORY_ID, true))
                    .thenReturn(List.of());
            when(ticketNumberGenerator.generate(TENANT_ID, "SUP")).thenReturn("SUP-2024-000001");
            when(slaEngine.compute(any(Ticket.class), eq(4), eq(24)))
                    .thenReturn(new SlaDeadlines(
                            Instant.now().plus(4, ChronoUnit.HOURS),
                            Instant.now().plus(24, ChronoUnit.HOURS)));
            Ticket saved = TicketFixtures.openTicket(TENANT_ID);
            when(ticketRepository.save(any(Ticket.class))).thenReturn(saved);
            when(activityRepository.save(any(TicketActivity.class))).thenAnswer(inv -> inv.getArgument(0));

            // ACT
            Ticket result = ticketService.create(request, CUSTOMER_ID, TENANT_ID);

            // ASSERT — creation succeeded and tenantId is stamped on the returned ticket
            assertThat(result).isNotNull();
            assertThat(result.getTenantId())
                    .as("Created ticket must carry the caller-supplied tenantId")
                    .isEqualTo(TENANT_ID);
        }

        @Test
        @DisplayName("REQ-CUI-AUTH-05: tenantId is propagated from caller and stamped on every saved ticket")
        void shouldStampCallerTenantIdOnSavedTicket() {
            // ARRANGE
            CreateTicketRequest request = new CreateTicketRequest(
                    VALID_TITLE, VALID_DESCRIPTION, CATEGORY_ID, null, null, null, null);

            when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.of(activeCategory));
            when(slaPolicyRepository.findByTenantIdAndCategoryIdAndActive(TENANT_ID, CATEGORY_ID, true))
                    .thenReturn(List.of());
            when(ticketNumberGenerator.generate(TENANT_ID, "SUP")).thenReturn("SUP-2024-000001");
            when(slaEngine.compute(any(Ticket.class), eq(4), eq(24)))
                    .thenReturn(new SlaDeadlines(
                            Instant.now().plus(4, ChronoUnit.HOURS),
                            Instant.now().plus(24, ChronoUnit.HOURS)));
            when(ticketRepository.save(any(Ticket.class))).thenAnswer(inv -> {
                Ticket t = inv.getArgument(0);
                t.setId(UUID.randomUUID());
                return t;
            });
            when(activityRepository.save(any(TicketActivity.class))).thenAnswer(inv -> inv.getArgument(0));

            // ACT
            ticketService.create(request, CUSTOMER_ID, TENANT_ID);

            // ASSERT — capture what was passed to ticketRepository.save() and verify tenantId
            ArgumentCaptor<Ticket> savedCaptor = ArgumentCaptor.forClass(Ticket.class);
            verify(ticketRepository).save(savedCaptor.capture());

            Ticket persisted = savedCaptor.getValue();
            assertThat(persisted.getTenantId())
                    .as("Persisted ticket tenantId must match the tenantId supplied by the caller")
                    .isEqualTo(TENANT_ID);
            assertThat(persisted.getTenantId())
                    .as("tenantId must never be null on a persisted ticket")
                    .isNotNull();
        }

        @Test
        @DisplayName("REQ-CUI-AUTH-05: should throw when tenantId is null (missing auth context)")
        void shouldThrowWhenTenantIdIsNull() {
            // ARRANGE — simulates an unauthenticated call where tenantId was not set by the gateway
            CreateTicketRequest request = new CreateTicketRequest(
                    VALID_TITLE, VALID_DESCRIPTION, CATEGORY_ID, null, null, null, null);

            // Category lookup requires the tenantId for the tenant-isolation filter check;
            // passing null makes the tenant-isolation filter reject the category,
            // so the service throws CategoryNotFoundException (category not found for tenant null).
            when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.of(activeCategory));

            // ACT + ASSERT — service must not accept a null tenantId; it throws a RuntimeException
            assertThatThrownBy(() -> ticketService.create(request, CUSTOMER_ID, null))
                    .isInstanceOf(RuntimeException.class);
        }
    }
}
