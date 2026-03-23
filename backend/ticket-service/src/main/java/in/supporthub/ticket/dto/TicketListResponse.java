package in.supporthub.ticket.dto;

import in.supporthub.ticket.domain.Priority;
import in.supporthub.ticket.domain.SentimentLabel;
import in.supporthub.ticket.domain.Ticket;
import in.supporthub.ticket.domain.TicketStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Lightweight ticket summary — returned in paginated list responses.
 *
 * <p>Omits large fields (description, customFields) to reduce response size.
 */
public record TicketListResponse(
        UUID id,
        String ticketNumber,
        String title,
        TicketStatus status,
        Priority priority,
        UUID categoryId,
        UUID assignedAgentId,
        SentimentLabel sentimentLabel,
        boolean slaResolutionBreached,
        Instant createdAt,
        Instant updatedAt
) {

    /**
     * Maps a {@link Ticket} entity to a {@link TicketListResponse} DTO.
     *
     * @param ticket the source ticket entity
     * @return the mapped list response DTO
     */
    public static TicketListResponse from(Ticket ticket) {
        return new TicketListResponse(
                ticket.getId(),
                ticket.getTicketNumber(),
                ticket.getTitle(),
                ticket.getStatus(),
                ticket.getPriority(),
                ticket.getCategoryId(),
                ticket.getAssignedAgentId(),
                ticket.getSentimentLabel(),
                ticket.isSlaResolutionBreached(),
                ticket.getCreatedAt(),
                ticket.getUpdatedAt()
        );
    }
}
