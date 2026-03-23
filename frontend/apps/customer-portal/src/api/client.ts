import { SupportHubClient } from '@supporthub/customer-sdk';
import { useAuthStore } from '../store/authStore.js';

export function getApiClient(tenantId: string): SupportHubClient {
  return new SupportHubClient({
    baseUrl: import.meta.env.VITE_API_BASE_URL as string,
    tenantId,
    getAccessToken: () => {
      const token = useAuthStore.getState().token;
      return token ?? '';
    },
  });
}
