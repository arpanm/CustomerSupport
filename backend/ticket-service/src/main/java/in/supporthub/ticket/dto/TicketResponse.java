package in.supporthub.ticket.dto;

import in.supporthub.ticket.domain.ActorType;
import in.supporthub.ticket.domain.Channel;
import in.supporthub.ticket.domain.Priority;
import in.supporthub.ticket.domain.SentimentLabel;
import in.supporthub.ticket.domain.Ticket;
import in.supporthub.ticket.domain.TicketStatus;
import in.supporthub.ticket.domain.TicketType;

import java.time.Instant;
import java.util.UUID;

/**
 * Full ticket detail response — returned by GET /api/v1/tickets/{ticketNumber}.
 */
public record TicketResponse(
        UUID id,
        String ticketNumber,
        UUID tenantId,
        UUID customerId,
        UUID orderId,
        String title,
        String description,
        UUID categoryId,
        UUID subCategoryId,
        TicketType ticketType,
        Priority priority,
        TicketStatus status,
        Channel channel,
        UUID assignedAgentId,
        UUID assignedTeamId,
        String[] tags,
        Instant slaFirstResponseDueAt,
        Instant slaResolutionDueAt,
        boolean slaFirstResponseBreached,
        boolean slaResolutionBreached,
        Float sentimentScore,
        SentimentLabel sentimentLabel,
        Instant sentimentUpdatedAt,
        Instant firstRespondedAt,
        Instant resolvedAt,
        Instant closedAt,
        Instant createdAt,
        Instant updatedAt
) {

    /**
     * Maps a {@link Ticket} entity to a {@link TicketResponse} DTO.
     *
     * @param ticket the source ticket entity
     * @return the mapped response DTO
     */
    public static TicketResponse from(Ticket ticket) {
        return new TicketResponse(
                ticket.getId(),
                ticket.getTicketNumber(),
                ticket.getTenantId(),
                ticket.getCustomerId(),
                ticket.getOrderId(),
                ticket.getTitle(),
                ticket.getDescription(),
                ticket.getCategoryId(),
                ticket.getSubCategoryId(),
                ticket.getTicketType(),
                ticket.getPriority(),
                ticket.getStatus(),
                ticket.getChannel(),
                ticket.getAssignedAgentId(),
                ticket.getAssignedTeamId(),
                ticket.getTags(),
                ticket.getSlaFirstResponseDueAt(),
                ticket.getSlaResolutionDueAt(),
                ticket.isSlaFirstResponseBreached(),
                ticket.isSlaResolutionBreached(),
                ticket.getSentimentScore(),
                ticket.getSentimentLabel(),
                ticket.getSentimentUpdatedAt(),
                ticket.getFirstRespondedAt(),
                ticket.getResolvedAt(),
                ticket.getClosedAt(),
                ticket.getCreatedAt(),
                ticket.getUpdatedAt()
        );
    }
}
