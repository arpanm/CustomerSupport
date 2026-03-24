package in.supporthub.ticket.controller;

import in.supporthub.shared.dto.ApiResponse;
import in.supporthub.ticket.dto.PresignAttachmentRequest;
import in.supporthub.ticket.dto.PresignAttachmentResponse;
import in.supporthub.ticket.service.AttachmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * REST controller for file attachment operations.
 *
 * <p>All requests require:
 * <ul>
 *   <li>{@code X-Tenant-ID} header — tenant UUID set by the API gateway after JWT validation.</li>
 *   <li>{@code X-User-Id} header — authenticated user UUID set by the API gateway.</li>
 * </ul>
 *
 * <p>The tenant ID is NEVER accepted from the request body — it is always read
 * from the {@code X-Tenant-ID} header to prevent tenant impersonation.
 */
@RestController
@RequestMapping("/api/v1/attachments")
@Tag(name = "Attachment API", description = "File attachment presigned URL generation for MinIO uploads")
@Slf4j
@RequiredArgsConstructor
public class AttachmentController {

    static final String HEADER_TENANT_ID = "X-Tenant-ID";
    static final String HEADER_USER_ID = "X-User-Id";

    private final AttachmentService attachmentService;

    /**
     * Generates a MinIO presigned PUT URL for a new file attachment.
     *
     * <p>Flow:
     * <ol>
     *   <li>Client calls this endpoint with {@code fileName} and {@code contentType}.</li>
     *   <li>Server records an {@code Attachment} entity with status {@code PENDING} and returns a presigned URL + attachment ID.</li>
     *   <li>Client PUTs the file bytes directly to the presigned URL (no data flows through the backend).</li>
     *   <li>Client includes the {@code attachmentId} in the subsequent {@code POST /api/v1/tickets} request.</li>
     * </ol>
     *
     * @param tenantId tenant UUID from the {@code X-Tenant-ID} header
     * @param userId   authenticated user UUID from the {@code X-User-Id} header
     * @param request  the file name and MIME type
     * @return presigned upload URL and attachment UUID
     */
    @PostMapping("/presign")
    @Operation(summary = "Generate a presigned MinIO PUT URL for a file upload")
    public ResponseEntity<ApiResponse<PresignAttachmentResponse>> presign(
            @RequestHeader(HEADER_TENANT_ID) UUID tenantId,
            @RequestHeader(HEADER_USER_ID) UUID userId,
            @Valid @RequestBody PresignAttachmentRequest request
    ) {
        log.info("attachment.presign.request: tenantId={} userId={}", tenantId, userId);

        PresignAttachmentResponse response = attachmentService.presignUpload(tenantId, request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }
}
