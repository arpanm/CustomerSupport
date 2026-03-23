package in.supporthub.ticket.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for reopening a resolved or closed ticket.
 */
public record ReopenTicketRequest(

        @NotBlank(message = "Reason must not be blank")
        @Size(min = 5, max = 2000, message = "Reason must be between 5 and 2000 characters")
        String reason
) {}
