package in.supporthub.ticket.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * WebSocket handshake interceptor that validates JWT tokens and tenant context
 * before allowing a STOMP connection to be established.
 *
 * <p>The API gateway has already performed full signature verification; this
 * interceptor only decodes the JWT payload (base64url) to extract claims for
 * role-based access control and to populate the WebSocket session attributes.
 *
 * <p>Connection is rejected ({@code 401}) if:
 * <ul>
 *   <li>No {@code token} query parameter is present.</li>
 *   <li>No {@code tenantId} query parameter is present.</li>
 *   <li>The JWT payload cannot be decoded.</li>
 * </ul>
 *
 * <p>Connection is rejected ({@code 403}) if the caller's role is not
 * {@code AGENT}, {@code ADMIN}, or {@code SUPER_ADMIN}. Customers may not
 * subscribe to the agent STOMP endpoint.
 *
 * <p>On success the following attributes are stored in the WebSocket session:
 * <ul>
 *   <li>{@code tenantId} — the tenant UUID string.</li>
 *   <li>{@code agentId}  — the subject claim from the JWT ({@code sub}).</li>
 *   <li>{@code role}     — the role claim from the JWT ({@code role}).</li>
 * </ul>
 */
@Component
@Slf4j
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    /**
     * Validates the incoming WebSocket upgrade request.
     *
     * @param request    The incoming upgrade request.
     * @param response   The upgrade response (used to set error status codes).
     * @param wsHandler  The target WebSocket handler.
     * @param attributes Mutable session attributes map — populated on success.
     * @return {@code true} to allow the connection; {@code false} to reject it.
     */
    @Override
    public boolean beforeHandshake(ServerHttpRequest request,
                                   ServerHttpResponse response,
                                   WebSocketHandler wsHandler,
                                   Map<String, Object> attributes) {

        if (!(request instanceof ServletServerHttpRequest servletRequest)) {
            log.warn("WS handshake rejected: request is not a ServletServerHttpRequest");
            response.setStatusCode(HttpStatus.BAD_REQUEST);
            return false;
        }

        HttpServletRequest httpRequest = servletRequest.getServletRequest();
        String query = httpRequest.getQueryString();

        if (query == null || query.isBlank()) {
            log.warn("WS handshake rejected: missing query string (token + tenantId required)");
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }

        Map<String, String> params = parseQueryString(query);
        String token = params.get("token");
        String tenantId = params.get("tenantId");

        if (token == null || token.isBlank()) {
            log.warn("WS handshake rejected: token query parameter absent");
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }

        if (tenantId == null || tenantId.isBlank()) {
            log.warn("WS handshake rejected: tenantId query parameter absent");
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }

        // Decode JWT payload — gateway already validated the signature; we only
        // need the claims to enforce role-based WebSocket access.
        try {
            String[] parts = token.split("\\.");
            if (parts.length < 2) {
                log.warn("WS handshake rejected: malformed JWT (fewer than 2 segments)");
                response.setStatusCode(HttpStatus.UNAUTHORIZED);
                return false;
            }

            // part[1] is the base64url-encoded payload
            byte[] payloadBytes = Base64.getUrlDecoder().decode(parts[1]);
            String payloadJson = new String(payloadBytes, StandardCharsets.UTF_8);

            ObjectMapper mapper = new ObjectMapper();
            Map<?, ?> claims = mapper.readValue(payloadJson, Map.class);

            String sub = (String) claims.get("sub");
            String role = (String) claims.get("role");

            if (sub == null || sub.isBlank()) {
                log.warn("WS handshake rejected: JWT missing 'sub' claim, tenantId={}", tenantId);
                response.setStatusCode(HttpStatus.UNAUTHORIZED);
                return false;
            }

            // Only agents and admins are permitted to connect to the agent endpoint
            if (!"AGENT".equals(role) && !"ADMIN".equals(role) && !"SUPER_ADMIN".equals(role)) {
                log.warn("WS handshake rejected: insufficient role='{}' for tenantId={}", role, tenantId);
                response.setStatusCode(HttpStatus.FORBIDDEN);
                return false;
            }

            attributes.put("tenantId", tenantId);
            attributes.put("agentId", sub);
            attributes.put("role", role);

            log.debug("WS handshake accepted: agentId={}, tenantId={}, role={}", sub, tenantId, role);
            return true;

        } catch (Exception e) {
            log.warn("WS handshake rejected: JWT decode error tenantId={} error={}",
                    tenantId, e.getMessage());
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
    }

    /**
     * No-op post-handshake hook.
     */
    @Override
    public void afterHandshake(ServerHttpRequest request,
                                ServerHttpResponse response,
                                WebSocketHandler handler,
                                Exception exception) {
        // Nothing to do after handshake
    }

    /**
     * Parses a URL query string into a key-value map.
     * Both keys and values are URL-decoded.
     *
     * @param query Raw query string (e.g., {@code token=abc&tenantId=xyz}).
     * @return Mutable map of decoded query parameters.
     */
    private Map<String, String> parseQueryString(String query) {
        Map<String, String> map = new HashMap<>();
        for (String pair : query.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2) {
                try {
                    String key = URLDecoder.decode(kv[0], StandardCharsets.UTF_8);
                    String value = URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
                    map.put(key, value);
                } catch (IllegalArgumentException ignored) {
                    // Skip malformed percent-encoding — don't fail the whole parse
                }
            }
        }
        return map;
    }
}
