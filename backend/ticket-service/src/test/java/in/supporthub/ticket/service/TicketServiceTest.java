package in.supporthub.ticket.service;

import in.supporthub.shared.event.TicketCreatedEvent;
import in.supporthub.shared.event.TicketStatusChangedEvent;
import in.supporthub.ticket.domain.Priority;
import in.supporthub.ticket.domain.SentimentLabel;
import in.supporthub.ticket.domain.Ticket;
import in.supporthub.ticket.domain.TicketActivity;
import in.supporthub.ticket.domain.TicketCategory;
import in.supporthub.ticket.domain.TicketStatus;
import in.supporthub.ticket.dto.CreateTicketRequest;
import in.supporthub.ticket.dto.ResolveTicketRequest;
import in.supporthub.ticket.exception.CategoryNotFoundException;
import in.supporthub.ticket.exception.InvalidStatusTransitionException;
import in.supporthub.ticket.exception.TicketNotFoundException;
import in.supporthub.ticket.fixtures.TicketFixtures;
import in.supporthub.ticket.repository.SlaPolicyRepository;
import in.supporthub.ticket.repository.TicketActivityRepository;
import in.supporthub.ticket.repository.TicketCategoryRepository;
import in.supporthub.ticket.repository.TicketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link TicketService} — the core business logic layer.
 *
 * <p>All external dependencies are mocked; no Spring context is loaded.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TicketService")
class TicketServiceTest {

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

    private static final UUID TENANT_ID = TicketFixtures.DEFAULT_TENANT_ID;
    private static final UUID CUSTOMER_ID = TicketFixtures.DEFAULT_CUSTOMER_ID;
    private static final UUID CATEGORY_ID = TicketFixtures.DEFAULT_CATEGORY_ID;
    private static final UUID AGENT_ID = UUID.randomUUID();

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
    // CREATE
    // =========================================================================

    @Nested
    @DisplayName("create()")
    class CreateTests {

        @Test
        @DisplayName("Should create ticket, save it, and publish ticket.created event")
        void shouldCreateTicketAndPublishEvent() {
            // ARRANGE
            CreateTicketRequest request = new CreateTicketRequest(
                    "My order did not arrive",
                    "I placed order #12345 five days ago and it has not arrived yet.",
                    CATEGORY_ID, null, null, null, null);

            when(categoryRepository.findById(CATEGORY_ID))
                    .thenReturn(Optional.of(activeCategory));
            when(slaPolicyRepository.findByTenantIdAndCategoryIdAndActive(TENANT_ID, CATEGORY_ID, true))
                    .thenReturn(List.of());
            when(ticketNumberGenerator.generate(TENANT_ID, "SUP"))
                    .thenReturn("SUP-2024-000001");

            Ticket savedTicket = TicketFixtures.openTicket(TENANT_ID).toBuilder()
                    .ticketNumber("SUP-2024-000001")
                    .build();
            when(slaEngine.compute(any(Ticket.class), eq(4), eq(24)))
                    .thenReturn(new SlaDeadlines(
                            Instant.now().plus(4, ChronoUnit.HOURS),
                            Instant.now().plus(24, ChronoUnit.HOURS)));
            when(ticketRepository.save(any(Ticket.class))).thenReturn(savedTicket);
            when(activityRepository.save(any(TicketActivity.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            // ACT
            Ticket result = ticketService.create(request, CUSTOMER_ID, TENANT_ID);

            // ASSERT
            assertThat(result).isNotNull();
            assertThat(result.getTicketNumber()).isEqualTo("SUP-2024-000001");
            assertThat(result.getStatus()).isEqualTo(TicketStatus.OPEN);

            // Verify event was published
            ArgumentCaptor<TicketCreatedEvent> eventCaptor =
                    ArgumentCaptor.forClass(TicketCreatedEvent.class);
            verify(eventPublisher).publishTicketCreated(eventCaptor.capture());

            TicketCreatedEvent publishedEvent = eventCaptor.getValue();
            assertThat(publishedEvent.tenantId()).isEqualTo(TENANT_ID.toString());
            assertThat(publishedEvent.payload().ticketNumber()).isEqualTo("SUP-2024-000001");
        }

        @Test
        @DisplayName("Should throw CategoryNotFoundException when category does not exist")
        void shouldThrowWhenCategoryNotFound() {
            // ARRANGE
            CreateTicketRequest request = new CreateTicketRequest(
                    "My order did not arrive",
                    "I placed order #12345 five days ago and it has not arrived yet.",
                    CATEGORY_ID, null, null, null, null);

            when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.empty());

            // ACT + ASSERT
            assertThatThrownBy(() -> ticketService.create(request, CUSTOMER_ID, TENANT_ID))
                    .isInstanceOf(CategoryNotFoundException.class);
        }

        @Test
        @DisplayName("Should throw CategoryNotFoundException when category belongs to different tenant")
        void shouldThrowWhenCategoryBelongsToDifferentTenant() {
            // ARRANGE
            UUID otherTenantId = UUID.randomUUID();
            TicketCategory wrongTenantCategory = activeCategory.toBuilder()
                    .tenantId(otherTenantId)
                    .build();

            CreateTicketRequest request = new CreateTicketRequest(
                    "My order did not arrive",
                    "I placed order #12345 five days ago and it has not arrived yet.",
                    CATEGORY_ID, null, null, null, null);

            when(categoryRepository.findById(CATEGORY_ID))
                    .thenReturn(Optional.of(wrongTenantCategory));

            // ACT + ASSERT
            assertThatThrownBy(() -> ticketService.create(request, CUSTOMER_ID, TENANT_ID))
                    .isInstanceOf(CategoryNotFoundException.class);
        }
    }

    // =========================================================================
    // GET BY TICKET NUMBER
    // =========================================================================

    @Nested
    @DisplayName("getByTicketNumber()")
    class GetByTicketNumberTests {

        @Test
        @DisplayName("Should return ticket when found")
        void shouldReturnTicketWhenFound() {
            // ARRANGE
            Ticket ticket = TicketFixtures.openTicket(TENANT_ID);
            when(ticketRepository.findByTenantIdAndTicketNumber(TENANT_ID, ticket.getTicketNumber()))
                    .thenReturn(Optional.of(ticket));

            // ACT
            Ticket result = ticketService.getByTicketNumber(ticket.getTicketNumber(), TENANT_ID);

            // ASSERT
            assertThat(result).isEqualTo(ticket);
        }

        @Test
        @DisplayName("Should throw TicketNotFoundException when ticket does not exist")
        void shouldThrowWhenTicketNotFound() {
            // ARRANGE
            when(ticketRepository.findByTenantIdAndTicketNumber(TENANT_ID, "NONEXISTENT-001"))
                    .thenReturn(Optional.empty());

            // ACT + ASSERT
            assertThatThrownBy(() -> ticketService.getByTicketNumber("NONEXISTENT-001", TENANT_ID))
                    .isInstanceOf(TicketNotFoundException.class)
                    .hasMessageContaining("NONEXISTENT-001");
        }
    }

    // =========================================================================
    // RESOLVE
    // =========================================================================

    @Nested
    @DisplayName("resolve()")
    class ResolveTests {

        @Test
        @DisplayName("Should resolve ticket, set resolvedAt, and publish status-changed event")
        void shouldResolveTicketAndPublishEvent() {
            // ARRANGE
            Ticket ticket = TicketFixtures.ticketWithStatus(TENANT_ID, TicketStatus.IN_PROGRESS);
            when(ticketRepository.findByTenantIdAndTicketNumber(TENANT_ID, ticket.getTicketNumber()))
                    .thenReturn(Optional.of(ticket));
            when(ticketRepository.save(any(Ticket.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            when(activityRepository.save(any(TicketActivity.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            ResolveTicketRequest request = new ResolveTicketRequest(
                    "Issue has been resolved by replacing the order.");

            // ACT
            Ticket result = ticketService.resolve(ticket.getTicketNumber(), request, AGENT_ID, TENANT_ID);

            // ASSERT
            assertThat(result.getStatus()).isEqualTo(TicketStatus.RESOLVED);
            assertThat(result.getResolvedAt()).isNotNull();

            // Verify event published
            ArgumentCaptor<TicketStatusChangedEvent> eventCaptor =
                    ArgumentCaptor.forClass(TicketStatusChangedEvent.class);
            verify(eventPublisher).publishStatusChanged(eventCaptor.capture());

            TicketStatusChangedEvent event = eventCaptor.getValue();
            assertThat(event.payload().newStatus()).isEqualTo("RESOLVED");
            assertThat(event.payload().previousStatus()).isEqualTo("IN_PROGRESS");
            assertThat(event.payload().resolutionNote()).isEqualTo(request.resolution());
        }

        @Test
        @DisplayName("Should throw InvalidStatusTransitionException when resolving a CLOSED ticket")
        void shouldThrowWhenResolvingClosedTicket() {
            // ARRANGE
            Ticket ticket = TicketFixtures.ticketWithStatus(TENANT_ID, TicketStatus.CLOSED);
            when(ticketRepository.findByTenantIdAndTicketNumber(TENANT_ID, ticket.getTicketNumber()))
                    .thenReturn(Optional.of(ticket));

            ResolveTicketRequest request = new ResolveTicketRequest(
                    "Attempting to resolve a closed ticket — should fail.");

            // ACT + ASSERT
            assertThatThrownBy(() ->
                    ticketService.resolve(ticket.getTicketNumber(), request, AGENT_ID, TENANT_ID))
                    .isInstanceOf(InvalidStatusTransitionException.class)
                    .hasMessageContaining("CLOSED")
                    .hasMessageContaining("RESOLVED");
        }

        @Test
        @DisplayName("Should throw InvalidStatusTransitionException when resolving an OPEN ticket directly")
        void shouldThrowWhenResolvingOpenTicket() {
            // ARRANGE — OPEN → RESOLVED is NOT a valid transition
            Ticket ticket = TicketFixtures.openTicket(TENANT_ID);
            when(ticketRepository.findByTenantIdAndTicketNumber(TENANT_ID, ticket.getTicketNumber()))
                    .thenReturn(Optional.of(ticket));

            ResolveTicketRequest request = new ResolveTicketRequest(
                    "Attempting to resolve an open ticket — should fail.");

            // ACT + ASSERT
            assertThatThrownBy(() ->
                    ticketService.resolve(ticket.getTicketNumber(), request, AGENT_ID, TENANT_ID))
                    .isInstanceOf(InvalidStatusTransitionException.class);
        }
    }

    // =========================================================================
    // UPDATE SENTIMENT
    // =========================================================================

    @Nested
    @DisplayName("updateSentiment()")
    class UpdateSentimentTests {

        @Test
        @DisplayName("Should update sentiment fields when ticket is found")
        void shouldUpdateSentimentFields() {
            // ARRANGE
            Ticket ticket = TicketFixtures.openTicket(TENANT_ID);
            when(ticketRepository.findById(ticket.getId())).thenReturn(Optional.of(ticket));
            when(ticketRepository.save(any(Ticket.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            // ACT
            ticketService.updateSentiment(ticket.getId().toString(), SentimentLabel.NEGATIVE, -0.4f);

            // ASSERT
            ArgumentCaptor<Ticket> savedCaptor = ArgumentCaptor.forClass(Ticket.class);
            verify(ticketRepository).save(savedCaptor.capture());

            Ticket saved = savedCaptor.getValue();
            assertThat(saved.getSentimentLabel()).isEqualTo(SentimentLabel.NEGATIVE);
            assertThat(saved.getSentimentScore()).isEqualTo(-0.4f);
            assertThat(saved.getSentimentUpdatedAt()).isNotNull();
        }

        @Test
        @DisplayName("Should do nothing when ticket ID is not found")
        void shouldDoNothingWhenTicketNotFound() {
            // ARRANGE
            String unknownId = UUID.randomUUID().toString();
            when(ticketRepository.findById(UUID.fromString(unknownId))).thenReturn(Optional.empty());

            // ACT — should not throw
            ticketService.updateSentiment(unknownId, SentimentLabel.NEUTRAL, 0.0f);

            // ASSERT — no save was called
            verify(ticketRepository, org.mockito.Mockito.never()).save(any());
        }
    }
}
