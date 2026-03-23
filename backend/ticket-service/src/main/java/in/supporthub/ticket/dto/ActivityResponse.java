package in.supporthub.ticket.dto;

import in.supporthub.ticket.domain.ActivityType;
import in.supporthub.ticket.domain.ActorType;
import in.supporthub.ticket.domain.TicketActivity;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO for a single ticket activity.
 */
public record ActivityResponse(
        UUID id,
        UUID ticketId,
        UUID actorId,
        ActorType actorType,
        ActivityType activityType,
        String content,
        boolean isInternal,
        Instant createdAt
) {

    /**
     * Maps a {@link TicketActivity} entity to an {@link ActivityResponse} DTO.
     *
     * @param activity the source activity entity
     * @return the mapped response DTO
     */
    public static ActivityResponse from(TicketActivity activity) {
        return new ActivityResponse(
                activity.getId(),
                activity.getTicketId(),
                activity.getActorId(),
                activity.getActorType(),
                activity.getActivityType(),
                activity.getContent(),
                activity.isInternal(),
                activity.getCreatedAt()
        );
    }
}
