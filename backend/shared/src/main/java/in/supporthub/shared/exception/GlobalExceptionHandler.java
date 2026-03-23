package in.supporthub.shared.exception;

import in.supporthub.shared.dto.ApiError;
import in.supporthub.shared.dto.ApiError.ErrorDetail;
import in.supporthub.shared.dto.ResponseMeta;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Global exception handler for all SupportHub REST controllers.
 *
 * <p>Converts exceptions to the standard {@link ApiError} response envelope.
 * Every error response includes a {@code traceId} for correlating with server logs.
 *
 * <p>Logging policy:
 * <ul>
 *   <li>HTTP 4xx (client errors) → {@code log.info} — expected, not our fault.</li>
 *   <li>HTTP 5xx (server errors) → {@code log.error} with full stack trace — our fault.</li>
 * </ul>
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    private static final String API_VERSION = "v1";

    /**
     * Handles all {@link AppException} subclasses (typed business errors).
     */
    @ExceptionHandler(AppException.class)
    public ResponseEntity<ApiError> handleAppException(AppException ex, WebRequest request) {
        String traceId = UUID.randomUUID().toString();

        if (ex.getHttpStatus().is5xxServerError()) {
            log.error("Application error: traceId={}, errorCode={}, message={}",
                    traceId, ex.getErrorCode(), ex.getMessage(), ex);
        } else {
            log.info("Client error: traceId={}, errorCode={}, message={}",
                    traceId, ex.getErrorCode(), ex.getMessage());
        }

        ApiError response = buildError(ex.getErrorCode(), ex.getMessage(), null, traceId);
        return ResponseEntity.status(ex.getHttpStatus()).body(response);
    }

    /**
     * Handles {@link MethodArgumentNotValidException} — triggered by {@code @Valid} failures.
     * Returns HTTP 400 with field-level validation details.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidationException(
            MethodArgumentNotValidException ex, WebRequest request) {

        String traceId = UUID.randomUUID().toString();

        Map<String, Object> fieldErrors = new HashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(fieldError.getField(), fieldError.getDefaultMessage());
        }

        log.info("Validation failed: traceId={}, fields={}", traceId, fieldErrors.keySet());

        ApiError response = buildError(
                "VALIDATION_FAILED",
                "Request validation failed. Check the 'details' field for per-field errors.",
                fieldErrors,
                traceId
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    /**
     * Catch-all handler for any unhandled exception.
     * Returns HTTP 500 — always logs at ERROR level with full stack trace.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGenericException(Exception ex, WebRequest request) {
        String traceId = UUID.randomUUID().toString();

        log.error("Unhandled exception: traceId={}, type={}, message={}",
                traceId, ex.getClass().getSimpleName(), ex.getMessage(), ex);

        ApiError response = buildError(
                "INTERNAL_SERVER_ERROR",
                "An unexpected error occurred. Please try again later.",
                null,
                traceId
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private ApiError buildError(
            String code,
            String message,
            Map<String, Object> details,
            String traceId) {

        ErrorDetail errorDetail = new ErrorDetail(code, message, details, traceId);
        ResponseMeta meta = new ResponseMeta(UUID.randomUUID().toString(), Instant.now(), API_VERSION);
        return new ApiError(errorDetail, meta);
    }
}
