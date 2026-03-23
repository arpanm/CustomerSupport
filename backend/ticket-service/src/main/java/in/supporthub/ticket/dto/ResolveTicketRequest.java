package in.supporthub.ticket.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for resolving a ticket.
 */
public record ResolveTicketRequest(

        @NotBlank(message = "Resolution note must not be blank")
        @Size(min = 10, max = 5000, message = "Resolution note must be between 10 and 5000 characters")
        String resolution
) {}
