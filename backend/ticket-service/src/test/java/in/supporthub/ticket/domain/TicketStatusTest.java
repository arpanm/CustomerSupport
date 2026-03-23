package in.supporthub.ticket.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the {@link TicketStatus} state machine.
 *
 * <p>Verifies all documented valid transitions and rejects all invalid ones.
 */
@DisplayName("TicketStatus state machine")
class TicketStatusTest {

    // =========================================================================
    // VALID TRANSITIONS
    // =========================================================================

    @ParameterizedTest(name = "{0} → {1} should be ALLOWED")
    @CsvSource({
            // OPEN
            "OPEN, PENDING_AGENT_RESPONSE",
            "OPEN, IN_PROGRESS",
            "OPEN, ESCALATED",
            // PENDING_AGENT_RESPONSE
            "PENDING_AGENT_RESPONSE, PENDING_CUSTOMER_RESPONSE",
            "PENDING_AGENT_RESPONSE, IN_PROGRESS",
            // PENDING_CUSTOMER_RESPONSE
            "PENDING_CUSTOMER_RESPONSE, IN_PROGRESS",
            "PENDING_CUSTOMER_RESPONSE, PENDING_AGENT_RESPONSE",
            // IN_PROGRESS
            "IN_PROGRESS, RESOLVED",
            "IN_PROGRESS, ESCALATED",
            "IN_PROGRESS, PENDING_CUSTOMER_RESPONSE",
            // ESCALATED
            "ESCALATED, IN_PROGRESS",
            "ESCALATED, RESOLVED",
            // RESOLVED
            "RESOLVED, CLOSED",
            "RESOLVED, REOPENED",
            // CLOSED
            "CLOSED, REOPENED",
            // REOPENED
            "REOPENED, IN_PROGRESS",
            "REOPENED, RESOLVED",
    })
    @DisplayName("Valid transition: {0} → {1}")
    void validTransitions(TicketStatus from, TicketStatus to) {
        assertThat(from.canTransitionTo(to))
                .as("Expected %s → %s to be a valid transition", from, to)
                .isTrue();
    }

    // =========================================================================
    // INVALID TRANSITIONS
    // =========================================================================

    @ParameterizedTest(name = "{0} → {1} should be REJECTED")
    @CsvSource({
            // OPEN cannot go backwards or skip ahead
            "OPEN, RESOLVED",
            "OPEN, CLOSED",
            "OPEN, REOPENED",
            "OPEN, PENDING_CUSTOMER_RESPONSE",
            "OPEN, OPEN",
            // PENDING_AGENT_RESPONSE cannot jump ahead
            "PENDING_AGENT_RESPONSE, OPEN",
            "PENDING_AGENT_RESPONSE, RESOLVED",
            "PENDING_AGENT_RESPONSE, CLOSED",
            "PENDING_AGENT_RESPONSE, ESCALATED",
            // RESOLVED cannot go to IN_PROGRESS directly
            "RESOLVED, OPEN",
            "RESOLVED, IN_PROGRESS",
            "RESOLVED, ESCALATED",
            // CLOSED cannot go to IN_PROGRESS directly
            "CLOSED, IN_PROGRESS",
            "CLOSED, RESOLVED",
            "CLOSED, CLOSED",
    })
    @DisplayName("Invalid transition: {0} → {1}")
    void invalidTransitions(TicketStatus from, TicketStatus to) {
        assertThat(from.canTransitionTo(to))
                .as("Expected %s → %s to be an INVALID transition", from, to)
                .isFalse();
    }

    // =========================================================================
    // TERMINAL STATES
    // =========================================================================

    @Test
    @DisplayName("RESOLVED should be a terminal status")
    void resolvedIsTerminal() {
        assertThat(TicketStatus.RESOLVED.isTerminal()).isTrue();
    }

    @Test
    @DisplayName("CLOSED should be a terminal status")
    void closedIsTerminal() {
        assertThat(TicketStatus.CLOSED.isTerminal()).isTrue();
    }

    @Test
    @DisplayName("OPEN should NOT be a terminal status")
    void openIsNotTerminal() {
        assertThat(TicketStatus.OPEN.isTerminal()).isFalse();
    }

    @Test
    @DisplayName("IN_PROGRESS should NOT be a terminal status")
    void inProgressIsNotTerminal() {
        assertThat(TicketStatus.IN_PROGRESS.isTerminal()).isFalse();
    }

    @Test
    @DisplayName("ESCALATED should NOT be a terminal status")
    void escalatedIsNotTerminal() {
        assertThat(TicketStatus.ESCALATED.isTerminal()).isFalse();
    }

    // =========================================================================
    // ALLOWED TRANSITIONS SET
    // =========================================================================

    @Test
    @DisplayName("OPEN.allowedTransitions() should contain exactly 3 statuses")
    void openAllowedTransitions() {
        assertThat(TicketStatus.OPEN.allowedTransitions())
                .containsExactlyInAnyOrder(
                        TicketStatus.PENDING_AGENT_RESPONSE,
                        TicketStatus.IN_PROGRESS,
                        TicketStatus.ESCALATED
                );
    }

    @Test
    @DisplayName("RESOLVED.allowedTransitions() should contain exactly CLOSED and REOPENED")
    void resolvedAllowedTransitions() {
        assertThat(TicketStatus.RESOLVED.allowedTransitions())
                .containsExactlyInAnyOrder(
                        TicketStatus.CLOSED,
                        TicketStatus.REOPENED
                );
    }
}
