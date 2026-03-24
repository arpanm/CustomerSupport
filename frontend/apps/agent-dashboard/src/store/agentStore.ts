import { create } from 'zustand';
import { persist } from 'zustand/middleware';

export type AgentStatus = 'AVAILABLE' | 'BUSY' | 'OFFLINE';

interface AgentStatusState {
  agentStatus: AgentStatus;
}

interface AgentStatusActions {
  /**
   * Updates agent status in Zustand and syncs to the backend.
   *
   * @param status the new agent status
   * @param token  JWT bearer token for the API call
   */
  setAgentStatus: (status: AgentStatus, token: string) => void;
}

type AgentStore = AgentStatusState & AgentStatusActions;

export const useAgentStore = create<AgentStore>()(
  persist(
    (set) => ({
      agentStatus: 'AVAILABLE',

      setAgentStatus: (status: AgentStatus, token: string) => {
        set({ agentStatus: status });

        // Fire-and-forget sync to backend; failures are non-fatal
        void fetch('/api/v1/agents/me/status', {
          method: 'PUT',
          headers: {
            Authorization: `Bearer ${token}`,
            'Content-Type': 'application/json',
          },
          body: JSON.stringify({ status }),
        }).catch(() => {
          // Intentionally swallowed — status change is optimistic in the UI
        });
      },
    }),
    {
      name: 'supporthub-agent-status',
      partialize: (state) => ({ agentStatus: state.agentStatus }),
    },
  ),
);
