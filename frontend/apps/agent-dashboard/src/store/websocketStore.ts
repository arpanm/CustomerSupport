/**
 * WebSocket store using STOMP over SockJS for real-time ticket updates.
 *
 * Note: @stomp/stompjs and sockjs-client are not listed in package.json.
 * This implementation uses a raw WebSocket fallback that mimics STOMP frame
 * semantics. When the packages are added to package.json, replace the
 * RawStompClient with:
 *
 *   import SockJS from 'sockjs-client';
 *   import { Client } from '@stomp/stompjs';
 *
 * The public API (connect / disconnect / Zustand state shape) is identical
 * either way, so no other files need changing.
 */
import { create } from 'zustand';

export type WsStatus = 'disconnected' | 'connecting' | 'connected' | 'error';

export interface TicketUpdatePayload {
  ticketId: string;
  event: string;
  timestamp: number;
}

interface WebSocketState {
  status: WsStatus;
  lastTicketUpdate: TicketUpdatePayload | null;
  /** Number of live ticket-update events received since last reset. */
  updateCount: number;
}

interface WebSocketActions {
  connect: (tenantId: string, token: string) => void;
  disconnect: () => void;
  resetUpdateCount: () => void;
}

type WebSocketStore = WebSocketState & WebSocketActions;

// ---------------------------------------------------------------------------
// Internal connection state (kept outside Zustand to avoid serialisation)
// ---------------------------------------------------------------------------
let ws: WebSocket | null = null;
let reconnectTimeout: ReturnType<typeof setTimeout> | null = null;
let reconnectAttempt = 0;
let activeTenantId = '';
let activeToken = '';

const MAX_RECONNECT_ATTEMPTS = 6;
const BASE_RECONNECT_MS = 2000;

function getReconnectDelay(): number {
  // Exponential backoff: 2s, 4s, 8s, 16s, 32s, 64s
  return BASE_RECONNECT_MS * Math.pow(2, Math.min(reconnectAttempt, MAX_RECONNECT_ATTEMPTS - 1));
}

function scheduleReconnect(store: ReturnType<typeof create<WebSocketStore>>) {
  if (reconnectTimeout !== null) return;
  const delay = getReconnectDelay();
  reconnectAttempt += 1;
  reconnectTimeout = setTimeout(() => {
    reconnectTimeout = null;
    (store as unknown as { getState: () => WebSocketStore }).getState().connect(activeTenantId, activeToken);
  }, delay);
}

/**
 * Parses a raw STOMP-style message frame body.
 * Expected JSON shape: { type: 'TICKET_UPDATE', ticketId: string, event: string }
 */
function parseStompBody(raw: string): { type: string; ticketId: string; event: string } | null {
  // Raw WebSocket fallback sends plain JSON; real STOMP frames wrap the body
  // after two newlines. Handle both.
  const jsonStart = raw.indexOf('{');
  if (jsonStart === -1) return null;
  try {
    return JSON.parse(raw.slice(jsonStart)) as { type: string; ticketId: string; event: string };
  } catch {
    return null;
  }
}

export const useWebSocketStore = create<WebSocketStore>()((set) => {
  const storeRef = { getState: (): WebSocketStore => useWebSocketStore.getState() };
  void storeRef; // referenced inside closures below

  return {
    status: 'disconnected',
    lastTicketUpdate: null,
    updateCount: 0,

    connect: (tenantId: string, token: string) => {
      // Idempotency guard
      if (ws !== null && (ws.readyState === WebSocket.OPEN || ws.readyState === WebSocket.CONNECTING)) {
        return;
      }

      activeTenantId = tenantId;
      activeToken = token;

      set({ status: 'connecting' });

      /**
       * SockJS / STOMP connection.
       *
       * When @stomp/stompjs + sockjs-client are available:
       *
       *   const sockjsUrl = `${import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'}/ws/agent?token=${token}&tenantId=${tenantId}`;
       *   const client = new Client({
       *     webSocketFactory: () => new SockJS(sockjsUrl) as WebSocket,
       *     onConnect: () => {
       *       reconnectAttempt = 0;
       *       set({ status: 'connected' });
       *       client.subscribe(`/topic/tenant/${tenantId}/tickets`, (message) => {
       *         const msg = parseStompBody(message.body);
       *         if (msg?.type === 'TICKET_UPDATE') {
       *           set((s) => ({ lastTicketUpdate: { ticketId: msg.ticketId, event: msg.event, timestamp: Date.now() }, updateCount: s.updateCount + 1 }));
       *         }
       *       });
       *     },
       *     onDisconnect: () => { set({ status: 'disconnected' }); scheduleReconnect(useWebSocketStore); },
       *     onStompError: () => { set({ status: 'error' }); },
       *   });
       *   client.activate();
       *   return;
       *
       * Raw WebSocket fallback (current implementation):
       */
      const wsBase = (import.meta.env.VITE_WS_URL as string | undefined) ?? 'ws://localhost:8080';
      const wsUrl = `${wsBase}/ws/agent?token=${encodeURIComponent(token)}&tenantId=${encodeURIComponent(tenantId)}`;
      ws = new WebSocket(wsUrl);

      ws.onopen = () => {
        reconnectAttempt = 0;
        set({ status: 'connected' });
        if (reconnectTimeout !== null) {
          clearTimeout(reconnectTimeout);
          reconnectTimeout = null;
        }
        // Simulate STOMP SUBSCRIBE frame for servers that need it
        ws?.send(
          `SUBSCRIBE\nid:sub-0\ndestination:/topic/tenant/${tenantId}/tickets\n\n\0`,
        );
      };

      ws.onmessage = (event: MessageEvent<string>) => {
        const msg = parseStompBody(event.data);
        if (msg?.type === 'TICKET_UPDATE') {
          set((s) => ({
            lastTicketUpdate: {
              ticketId: msg.ticketId,
              event: msg.event,
              timestamp: Date.now(),
            },
            updateCount: s.updateCount + 1,
          }));
        }
      };

      ws.onerror = () => {
        set({ status: 'error' });
      };

      ws.onclose = () => {
        ws = null;
        set({ status: 'disconnected' });
        scheduleReconnect(useWebSocketStore as unknown as ReturnType<typeof create<WebSocketStore>>);
      };
    },

    disconnect: () => {
      if (reconnectTimeout !== null) {
        clearTimeout(reconnectTimeout);
        reconnectTimeout = null;
      }
      reconnectAttempt = 0;
      ws?.close();
      ws = null;
      set({ status: 'disconnected', lastTicketUpdate: null, updateCount: 0 });
    },

    resetUpdateCount: () => {
      set({ updateCount: 0 });
    },
  };
});
