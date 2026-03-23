import { create } from 'zustand';
import { persist } from 'zustand/middleware';

export interface AgentUser {
  id: string;
  name: string;
  email: string;
  role: 'AGENT' | 'SENIOR_AGENT' | 'ADMIN' | 'SUPER_ADMIN';
  tenantId: string;
  avatarUrl?: string;
}

interface AuthState {
  user: AgentUser | null;
  token: string | null;
  isAuthenticated: boolean;
}

interface AuthActions {
  login: (user: AgentUser, token: string) => void;
  logout: () => void;
  updateUser: (partial: Partial<AgentUser>) => void;
}

type AuthStore = AuthState & AuthActions;

export const useAuthStore = create<AuthStore>()(
  persist(
    (set) => ({
      user: null,
      token: null,
      isAuthenticated: false,

      login: (user: AgentUser, token: string) => {
        set({ user, token, isAuthenticated: true });
      },

      logout: () => {
        set({ user: null, token: null, isAuthenticated: false });
      },

      updateUser: (partial: Partial<AgentUser>) => {
        set((state) => ({
          user: state.user !== null ? { ...state.user, ...partial } : null,
        }));
      },
    }),
    {
      name: 'supporthub-agent-auth',
      partialize: (state) => ({
        user: state.user,
        token: state.token,
        isAuthenticated: state.isAuthenticated,
      }),
    },
  ),
);
