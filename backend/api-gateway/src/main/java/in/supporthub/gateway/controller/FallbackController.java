package in.supporthub.gateway.controller;

import in.supporthub.shared.dto.ApiError;
import in.supporthub.shared.dto.ResponseMeta;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.Instant;

/**
 * Fallback controller invoked by Resilience4j circuit breakers when a downstream service
 * is unavailable or timing out.
 *
 * <p>All fallback endpoints return HTTP 503 with a standard {@link ApiError} response body.
 */
@Slf4j
@RestController
@RequestMapping("/fallback")
public class FallbackController {

    private static final String SERVICE_UNAVAILABLE_CODE = "SERVICE_UNAVAILABLE";
    private static final String SERVICE_UNAVAILABLE_MESSAGE =
            "Service temporarily unavailable. Please try again in a few moments.";
    private static final String API_VERSION = "v1";

    /**
     * Generic service fallback — used by all circuit breakers that forward to {@code /fallback/service}.
     */
    @GetMapping("/service")
    public Mono<ResponseEntity<ApiError>> serviceUnavailableGet(
            @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        return Mono.just(buildServiceUnavailableResponse(requestId));
    }

    @PostMapping("/service")
    public Mono<ResponseEntity<ApiError>> serviceUnavailablePost(
            @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        return Mono.just(buildServiceUnavailableResponse(requestId));
    }

    /**
     * Ticket-service-specific fallback with a contextual message.
     */
    @GetMapping("/ticket")
    public Mono<ResponseEntity<ApiError>> ticketServiceUnavailable(
            @RequestHeader(value = "X-Request-Id", required = false) String requestId) {

        log.warn("Ticket service circuit open: requestId={}", requestId);

        ApiError apiError = new ApiError(
                new ApiError.ErrorDetail(
                        SERVICE_UNAVAILABLE_CODE,
                        "Ticket service is temporarily unavailable. Your request has been logged.",
                        null,
                        requestId),
                new ResponseMeta(requestId, Instant.now(), API_VERSION)
        );
        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(apiError));
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private ResponseEntity<ApiError> buildServiceUnavailableResponse(String requestId) {
        log.warn("Service circuit open — returning 503: requestId={}", requestId);

        ApiError apiError = new ApiError(
                new ApiError.ErrorDetail(
                        SERVICE_UNAVAILABLE_CODE,
                        SERVICE_UNAVAILABLE_MESSAGE,
                        null,
                        requestId),
                new ResponseMeta(requestId, Instant.now(), API_VERSION)
        );
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(apiError);
    }
}
