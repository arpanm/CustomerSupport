package in.supporthub.ticket.dto;

import in.supporthub.ticket.domain.Priority;
import in.supporthub.ticket.domain.TicketStatus;

import java.util.List;
import java.util.UUID;

/**
 * Request body for updating mutable ticket fields.
 *
 * <p>All fields are optional. Only non-null values are applied.
 * Use dedicated action endpoints ({@code /actions/resolve}, {@code /actions/escalate}, etc.)
 * for status transitions that carry additional semantics.
 */
public record UpdateTicketRequest(
        Priority priority,
        TicketStatus status,
        UUID assignedAgentId,
        UUID assignedTeamId,
        List<String> tags,
        String customFields
) {}
