import { create } from 'zustand';

type WsStatus = 'disconnected' | 'connecting' | 'connected' | 'error';

interface WebSocketState {
  status: WsStatus;
  lastTicketUpdate: { ticketId: string; event: string; timestamp: number } | null;
}

interface WebSocketActions {
  connect: (tenantId: string, token: string) => void;
  disconnect: () => void;
}

type WebSocketStore = WebSocketState & WebSocketActions;

let ws: WebSocket | null = null;
let reconnectTimeout: ReturnType<typeof setTimeout> | null = null;

export const useWebSocketStore = create<WebSocketStore>()((set) => ({
  status: 'disconnected',
  lastTicketUpdate: null,

  connect: (tenantId: string, token: string) => {
    if (ws?.readyState === WebSocket.OPEN) return;

    set({ status: 'connecting' });

    const wsUrl = `${import.meta.env.VITE_WS_URL ?? 'ws://localhost:8080'}/ws/agent?token=${token}&tenantId=${tenantId}`;
    ws = new WebSocket(wsUrl);

    ws.onopen = () => {
      set({ status: 'connected' });
      if (reconnectTimeout) {
        clearTimeout(reconnectTimeout);
        reconnectTimeout = null;
      }
    };

    ws.onmessage = (event: MessageEvent<string>) => {
      try {
        const msg = JSON.parse(event.data) as { type: string; ticketId: string; event: string };
        if (msg.type === 'TICKET_UPDATE') {
          set({
            lastTicketUpdate: {
              ticketId: msg.ticketId,
              event: msg.event,
              timestamp: Date.now(),
            },
          });
        }
      } catch {
        // ignore malformed messages
      }
    };

    ws.onerror = () => {
      set({ status: 'error' });
    };

    ws.onclose = () => {
      set({ status: 'disconnected' });
      // Auto-reconnect after 5s
      reconnectTimeout = setTimeout(() => {
        useWebSocketStore.getState().connect(tenantId, token);
      }, 5000);
    };
  },

  disconnect: () => {
    if (reconnectTimeout) {
      clearTimeout(reconnectTimeout);
      reconnectTimeout = null;
    }
    ws?.close();
    ws = null;
    set({ status: 'disconnected', lastTicketUpdate: null });
  },
}));
