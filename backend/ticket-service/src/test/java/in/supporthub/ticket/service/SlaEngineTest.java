package in.supporthub.ticket.service;

import in.supporthub.ticket.domain.Ticket;
import in.supporthub.ticket.domain.TicketStatus;
import in.supporthub.ticket.fixtures.TicketFixtures;
import in.supporthub.ticket.repository.TicketRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link SlaEngine}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SlaEngine")
class SlaEngineTest {

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private TicketEventPublisher ticketEventPublisher;

    @InjectMocks
    private SlaEngine slaEngine;

    private static final java.util.UUID TENANT_ID = TicketFixtures.DEFAULT_TENANT_ID;

    // =========================================================================
    // COMPUTE
    // =========================================================================

    @Nested
    @DisplayName("compute()")
    class ComputeTests {

        @Test
        @DisplayName("Should return first-response deadline = createdAt + firstResponseHours")
        void shouldComputeCorrectFirstResponseDeadline() {
            // ARRANGE
            Ticket ticket = TicketFixtures.openTicket(TENANT_ID);
            Instant expected = ticket.getCreatedAt().plus(4, ChronoUnit.HOURS);

            // ACT
            SlaDeadlines deadlines = slaEngine.compute(ticket, 4, 24);

            // ASSERT
            assertThat(deadlines.firstResponseDueAt()).isEqualTo(expected);
        }

        @Test
        @DisplayName("Should return resolution deadline = createdAt + resolutionHours")
        void shouldComputeCorrectResolutionDeadline() {
            // ARRANGE
            Ticket ticket = TicketFixtures.openTicket(TENANT_ID);
            Instant expected = ticket.getCreatedAt().plus(24, ChronoUnit.HOURS);

            // ACT
            SlaDeadlines deadlines = slaEngine.compute(ticket, 4, 24);

            // ASSERT
            assertThat(deadlines.resolutionDueAt()).isEqualTo(expected);
        }

        @Test
        @DisplayName("Should use current time as base when ticket createdAt is null")
        void shouldUseCurrentTimeWhenCreatedAtIsNull() {
            // ARRANGE
            Ticket ticket = Ticket.builder()
                    .id(java.util.UUID.randomUUID())
                    .ticketNumber("TEST-001")
                    .tenantId(TENANT_ID)
                    .build();
            Instant before = Instant.now();

            // ACT
            SlaDeadlines deadlines = slaEngine.compute(ticket, 2, 8);

            // ASSERT
            assertThat(deadlines.firstResponseDueAt())
                    .isAfterOrEqualTo(before.plus(2, ChronoUnit.HOURS));
            assertThat(deadlines.resolutionDueAt())
                    .isAfterOrEqualTo(before.plus(8, ChronoUnit.HOURS));
        }

        @Test
        @DisplayName("Should compute 48-hour resolution deadline correctly")
        void shouldComputeFortyEightHourResolution() {
            // ARRANGE
            Ticket ticket = TicketFixtures.openTicket(TENANT_ID);
            Instant expected = ticket.getCreatedAt().plus(48, ChronoUnit.HOURS);

            // ACT
            SlaDeadlines deadlines = slaEngine.compute(ticket, 2, 48);

            // ASSERT
            assertThat(deadlines.resolutionDueAt()).isEqualTo(expected);
        }
    }

    // =========================================================================
    // DETECT BREACHES
    // =========================================================================

    @Nested
    @DisplayName("detectBreaches()")
    class DetectBreachesTests {

        @Test
        @DisplayName("Should mark overdue tickets as slaResolutionBreached = true")
        void shouldMarkOverdueTicketsAsBreached() {
            // ARRANGE
            Ticket overdueTicket = TicketFixtures.overdueSlaTicket(TENANT_ID);
            assertThat(overdueTicket.isSlaResolutionBreached()).isFalse();

            when(ticketRepository.findSlaBreaching(any(Instant.class), anyList()))
                    .thenReturn(List.of(overdueTicket));
            when(ticketRepository.findFirstResponseSlaBreaching(any(Instant.class), anyList()))
                    .thenReturn(List.of());
            when(ticketRepository.save(any(Ticket.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            // ACT
            slaEngine.detectBreaches();

            // ASSERT
            ArgumentCaptor<Ticket> savedCaptor = ArgumentCaptor.forClass(Ticket.class);
            verify(ticketRepository).save(savedCaptor.capture());

            Ticket saved = savedCaptor.getValue();
            assertThat(saved.isSlaResolutionBreached()).isTrue();
        }

        @Test
        @DisplayName("Should mark first-response breached tickets correctly")
        void shouldMarkFirstResponseBreachedTickets() {
            // ARRANGE
            Ticket overdueTicket = TicketFixtures.overdueSlaTicket(TENANT_ID);

            when(ticketRepository.findSlaBreaching(any(Instant.class), anyList()))
                    .thenReturn(List.of());
            when(ticketRepository.findFirstResponseSlaBreaching(any(Instant.class), anyList()))
                    .thenReturn(List.of(overdueTicket));
            when(ticketRepository.save(any(Ticket.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            // ACT
            slaEngine.detectBreaches();

            // ASSERT
            ArgumentCaptor<Ticket> savedCaptor = ArgumentCaptor.forClass(Ticket.class);
            verify(ticketRepository).save(savedCaptor.capture());

            Ticket saved = savedCaptor.getValue();
            assertThat(saved.isSlaFirstResponseBreached()).isTrue();
        }

        @Test
        @DisplayName("Should do nothing when no tickets are breaching SLA")
        void shouldDoNothingWhenNoBreaches() {
            // ARRANGE
            when(ticketRepository.findSlaBreaching(any(Instant.class), anyList()))
                    .thenReturn(List.of());
            when(ticketRepository.findFirstResponseSlaBreaching(any(Instant.class), anyList()))
                    .thenReturn(List.of());

            // ACT
            slaEngine.detectBreaches();

            // ASSERT — no saves were made
            verify(ticketRepository, org.mockito.Mockito.never()).save(any());
        }

        @Test
        @DisplayName("Should exclude RESOLVED and CLOSED tickets from breach detection")
        void shouldExcludeTerminalStatusTickets() {
            // ARRANGE
            ArgumentCaptor<List<TicketStatus>> excludedCaptor =
                    ArgumentCaptor.forClass((Class<List<TicketStatus>>) (Class<?>) List.class);

            when(ticketRepository.findSlaBreaching(any(Instant.class), excludedCaptor.capture()))
                    .thenReturn(List.of());
            when(ticketRepository.findFirstResponseSlaBreaching(any(Instant.class), anyList()))
                    .thenReturn(List.of());

            // ACT
            slaEngine.detectBreaches();

            // ASSERT — RESOLVED and CLOSED must be in the exclusion list
            List<TicketStatus> excluded = excludedCaptor.getValue();
            assertThat(excluded)
                    .containsExactlyInAnyOrder(TicketStatus.RESOLVED, TicketStatus.CLOSED);
        }
    }
}
