package in.supporthub.gateway.exception;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.supporthub.shared.dto.ApiError;
import in.supporthub.shared.dto.ResponseMeta;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.cloud.gateway.support.NotFoundException;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Instant;

/**
 * Global exception handler for the reactive Gateway pipeline.
 *
 * <p>Catches exceptions that propagate out of filter chains (including circuit breaker
 * timeouts and unhandled runtime errors) and serialises them into the standard
 * {@link ApiError} JSON envelope.
 *
 * <p>Runs at {@code @Order(-2)} so it runs after Spring's built-in
 * {@code DefaultErrorWebExceptionHandler} (-1) is overridden.
 *
 * <p>Severity guidance:
 * <ul>
 *   <li>HTTP 4xx — INFO log (client error, not our fault)</li>
 *   <li>HTTP 5xx — ERROR log with stack trace (our fault)</li>
 * </ul>
 */
@Slf4j
@Order(-2)
@Component
@RequiredArgsConstructor
public class GatewayExceptionHandler implements ErrorWebExceptionHandler {

    private static final String API_VERSION = "v1";

    private final ObjectMapper objectMapper;

    @Override
    @NonNull
    public Mono<Void> handle(@NonNull ServerWebExchange exchange, @NonNull Throwable ex) {
        ServerHttpResponse response = exchange.getResponse();

        if (response.isCommitted()) {
            return Mono.error(ex);
        }

        String requestId = exchange.getRequest().getHeaders().getFirst("X-Request-Id");
        String path = exchange.getRequest().getPath().value();

        HttpStatus status;
        String code;
        String message;

        if (ex instanceof JwtException) {
            status = HttpStatus.UNAUTHORIZED;
            code = "INVALID_TOKEN";
            message = "Token is invalid or has expired.";
            log.info("JWT exception at path={}, requestId={}: {}", path, requestId, ex.getMessage());

        } else if (ex instanceof ResponseStatusException rse) {
            status = HttpStatus.resolve(rse.getStatusCode().value());
            if (status == null) {
                status = HttpStatus.INTERNAL_SERVER_ERROR;
            }
            code = status.is4xxClientError() ? "CLIENT_ERROR" : "SERVER_ERROR";
            message = rse.getReason() != null ? rse.getReason() : status.getReasonPhrase();
            if (status.is4xxClientError()) {
                log.info("Client error at path={}, requestId={}, status={}: {}",
                        path, requestId, status.value(), ex.getMessage());
            } else {
                log.error("Server error at path={}, requestId={}, status={}",
                        path, requestId, status.value(), ex);
            }

        } else if (ex instanceof NotFoundException) {
            status = HttpStatus.SERVICE_UNAVAILABLE;
            code = "SERVICE_UNAVAILABLE";
            message = "The requested service is currently unavailable.";
            log.warn("Service not found at path={}, requestId={}: {}", path, requestId, ex.getMessage());

        } else {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
            code = "INTERNAL_ERROR";
            message = "An unexpected error occurred. Please try again.";
            log.error("Unhandled gateway exception at path={}, requestId={}", path, requestId, ex);
        }

        return writeErrorResponse(response, status, code, message, requestId);
    }

    private Mono<Void> writeErrorResponse(
            ServerHttpResponse response,
            HttpStatus status,
            String code,
            String message,
            String requestId) {

        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        ApiError apiError = new ApiError(
                new ApiError.ErrorDetail(code, message, null, requestId),
                new ResponseMeta(requestId, Instant.now(), API_VERSION)
        );

        try {
            byte[] bytes = objectMapper.writeValueAsBytes(apiError);
            DataBuffer buffer = response.bufferFactory().wrap(bytes);
            return response.writeWith(Mono.just(buffer));
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize error response: requestId={}", requestId, e);
            return response.setComplete();
        }
    }
}
