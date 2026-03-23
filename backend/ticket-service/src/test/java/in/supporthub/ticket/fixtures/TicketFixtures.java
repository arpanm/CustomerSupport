package in.supporthub.ticket.fixtures;

import in.supporthub.ticket.domain.Channel;
import in.supporthub.ticket.domain.Priority;
import in.supporthub.ticket.domain.Ticket;
import in.supporthub.ticket.domain.TicketStatus;
import in.supporthub.ticket.domain.TicketType;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Test data factory for {@link Ticket} entities.
 *
 * <p>All test data MUST use these fixtures — no hardcoded values in test methods.
 */
public class TicketFixtures {

    public static final UUID DEFAULT_TENANT_ID =
            UUID.fromString("10000000-0000-0000-0000-000000000001");
    public static final UUID DEFAULT_CUSTOMER_ID =
            UUID.fromString("20000000-0000-0000-0000-000000000002");
    public static final UUID DEFAULT_CATEGORY_ID =
            UUID.fromString("30000000-0000-0000-0000-000000000003");

    private TicketFixtures() {
        // Utility class
    }

    /**
     * Creates a standard OPEN ticket for the given tenant.
     *
     * @param tenantId the tenant UUID
     * @return a fully populated Ticket with OPEN status
     */
    public static Ticket openTicket(UUID tenantId) {
        Instant now = Instant.now();
        return Ticket.builder()
                .id(UUID.randomUUID())
                .ticketNumber("TEST-2024-000001")
                .tenantId(tenantId)
                .customerId(DEFAULT_CUSTOMER_ID)
                .title("Test ticket for unit testing")
                .description("This is a test ticket description for unit testing purposes only.")
                .categoryId(DEFAULT_CATEGORY_ID)
                .ticketType(TicketType.INQUIRY)
                .priority(Priority.MEDIUM)
                .status(TicketStatus.OPEN)
                .channel(Channel.WEB)
                .slaFirstResponseDueAt(now.plus(4, ChronoUnit.HOURS))
                .slaResolutionDueAt(now.plus(24, ChronoUnit.HOURS))
                .slaFirstResponseBreached(false)
                .slaResolutionBreached(false)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    /**
     * Creates a ticket in the specified status for the given tenant.
     *
     * @param tenantId the tenant UUID
     * @param status   the desired ticket status
     * @return a Ticket with the specified status
     */
    public static Ticket ticketWithStatus(UUID tenantId, TicketStatus status) {
        return openTicket(tenantId).toBuilder()
                .status(status)
                .build();
    }

    /**
     * Creates a ticket with an SLA resolution deadline in the past (breaching).
     *
     * @param tenantId the tenant UUID
     * @return a Ticket with an overdue SLA
     */
    public static Ticket overdueSlaTicket(UUID tenantId) {
        Instant past = Instant.now().minus(1, ChronoUnit.HOURS);
        return openTicket(tenantId).toBuilder()
                .slaResolutionDueAt(past)
                .slaFirstResponseDueAt(past)
                .build();
    }

    /**
     * Creates a resolved ticket for the given tenant.
     *
     * @param tenantId the tenant UUID
     * @return a Ticket with RESOLVED status and resolvedAt timestamp
     */
    public static Ticket resolvedTicket(UUID tenantId) {
        return openTicket(tenantId).toBuilder()
                .status(TicketStatus.RESOLVED)
                .resolvedAt(Instant.now())
                .build();
    }
}
