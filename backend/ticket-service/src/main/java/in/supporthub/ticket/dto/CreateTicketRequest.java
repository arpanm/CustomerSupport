package in.supporthub.ticket.dto;

import in.supporthub.ticket.domain.Channel;
import in.supporthub.ticket.domain.TicketType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Request body for creating a new support ticket.
 */
public record CreateTicketRequest(

        @NotBlank(message = "Title must not be blank")
        @Size(min = 10, max = 200, message = "Title must be between 10 and 200 characters")
        String title,

        @NotBlank(message = "Description must not be blank")
        @Size(min = 20, max = 5000, message = "Description must be between 20 and 5000 characters")
        String description,

        @NotNull(message = "Category ID is required")
        UUID categoryId,

        UUID subCategoryId,

        UUID orderId,

        Channel channel,

        TicketType ticketType
) {}
