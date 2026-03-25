package in.supporthub.ticket.dto;

import java.util.UUID;

/**
 * Response body returned by {@code POST /api/v1/attachments/presign}.
 *
 * <p>The client must:
 * <ol>
 *   <li>Issue a {@code PUT} request to {@code uploadUrl} with the file bytes and the correct {@code Content-Type} header.</li>
 *   <li>Include {@code attachmentId} in the subsequent {@code CreateTicketRequest.attachmentIds} list.</li>
 * </ol>
 *
 * @param uploadUrl    MinIO presigned PUT URL (valid for 15 minutes)
 * @param attachmentId UUID of the newly created {@link in.supporthub.ticket.domain.Attachment} entity
 */
public record PresignAttachmentResponse(
        String uploadUrl,
        UUID attachmentId
) {}
