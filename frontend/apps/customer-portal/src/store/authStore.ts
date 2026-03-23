import { create } from 'zustand';
import { persist } from 'zustand/middleware';

interface AuthState {
  token: string | null;
  customerId: string | null;
  tenantId: string | null;
  setAuth: (token: string, customerId: string, tenantId: string) => void;
  clearAuth: () => void;
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      token: null,
      customerId: null,
      tenantId: null,
      setAuth: (token, customerId, tenantId) => {
        set({ token, customerId, tenantId });
      },
      clearAuth: () => {
        set({ token: null, customerId: null, tenantId: null });
      },
    }),
    {
      name: 'supporthub_auth',
    },
  ),
);
