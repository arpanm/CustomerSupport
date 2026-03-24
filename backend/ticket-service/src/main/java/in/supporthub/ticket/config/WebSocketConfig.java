package in.supporthub.ticket.config;

import in.supporthub.ticket.websocket.JwtHandshakeInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * STOMP / WebSocket configuration for real-time ticket updates.
 *
 * <p>Endpoint: {@code /ws/agent} (SockJS-enabled for browsers that lack native
 * WebSocket support). Clients must pass {@code ?token=JWT&tenantId=UUID} query
 * parameters — validated by {@link JwtHandshakeInterceptor}.
 *
 * <p>Topic namespace: {@code /topic/tenant/{tenantId}/tickets} — each tenant
 * receives its own isolated broadcast channel so agents cannot observe
 * cross-tenant events.
 *
 * <p>Application destination prefix: {@code /app} — reserved for future
 * client-to-server messaging (e.g., typing indicators).
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtHandshakeInterceptor jwtHandshakeInterceptor;

    /**
     * Configures the in-memory simple message broker and application destination prefix.
     *
     * <p>{@code /topic} — broker prefix for server-to-client broadcast topics.
     * {@code /app}   — prefix for messages routed to {@code @MessageMapping} methods.
     *
     * @param registry the broker registry to configure.
     */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }

    /**
     * Registers the STOMP endpoint that clients connect to.
     *
     * <p>SockJS fallback is enabled to support browsers without native WebSocket.
     * {@code setAllowedOriginPatterns("*")} delegates origin enforcement to the
     * API gateway; the ticket-service itself is an internal service not exposed directly.
     *
     * @param registry the STOMP endpoint registry.
     */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws/agent")
                .setAllowedOriginPatterns("*")
                .addInterceptors(jwtHandshakeInterceptor)
                .withSockJS();
    }
}
