package in.supporthub.ticket.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes real-time ticket updates to connected WebSocket clients via STOMP.
 *
 * <p>Messages are broadcast to the tenant-scoped topic
 * {@code /topic/tenant/{tenantId}/tickets}. Only agent-dashboard and
 * admin-portal clients subscribe to this topic; each must present a valid JWT
 * at handshake time (enforced by {@link JwtHandshakeInterceptor}).
 *
 * <p>WebSocket failures are non-fatal — a publish error must never propagate
 * to the calling ticket operation.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TicketWebSocketPublisher {

    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Broadcasts a {@link TicketUpdateMessage} to all agents subscribed to the
     * tenant's ticket topic.
     *
     * <p>Destination pattern: {@code /topic/tenant/{tenantId}/tickets}
     *
     * @param tenantId Tenant UUID string — used as part of the STOMP destination.
     * @param message  The update message to broadcast.
     */
    public void publishTicketUpdate(String tenantId, TicketUpdateMessage message) {
        String destination = "/topic/tenant/" + tenantId + "/tickets";
        try {
            messagingTemplate.convertAndSend(destination, message);
            log.debug("WS published: tenantId={}, ticketId={}, event={}",
                    tenantId, message.ticketId(), message.eventType());
        } catch (Exception e) {
            // Non-fatal: WS failure must never break ticket operations
            log.warn("WS publish failed: tenantId={}, ticketId={}, error={}",
                    tenantId, message.ticketId(), e.getMessage());
        }
    }
}
