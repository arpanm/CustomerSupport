package in.supporthub.ticket.dto;

import in.supporthub.ticket.domain.ActivityType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request body for adding a new activity (comment, note, etc.) to a ticket.
 */
public record AddActivityRequest(

        @NotBlank(message = "Content must not be blank")
        @Size(max = 10000, message = "Content must not exceed 10000 characters")
        String content,

        @NotNull(message = "Activity type is required")
        ActivityType activityType,

        boolean isInternal
) {}
