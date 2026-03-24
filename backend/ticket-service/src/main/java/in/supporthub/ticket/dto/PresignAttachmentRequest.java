package in.supporthub.ticket.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for generating a MinIO presigned PUT URL for a file attachment.
 */
public record PresignAttachmentRequest(

        @NotBlank(message = "fileName must not be blank")
        @Size(max = 500, message = "fileName must not exceed 500 characters")
        String fileName,

        @NotBlank(message = "contentType must not be blank")
        @Size(max = 200, message = "contentType must not exceed 200 characters")
        String contentType
) {}
