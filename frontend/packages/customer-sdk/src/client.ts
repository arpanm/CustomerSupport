import type {
  ApiResponse,
  ApiError,
  Ticket,
  TicketActivity,
  FaqEntry,
  FaqSearchResult,
  Notification,
  CreateTicketRequest,
  PaginatedResponse,
} from './types/index.js';
import { SupportHubError } from './types/index.js';

export interface SupportHubClientConfig {
  baseUrl: string;
  tenantId: string;
  getAccessToken: () => Promise<string> | string;
}

export class SupportHubClient {
  private readonly config: SupportHubClientConfig;

  constructor(config: SupportHubClientConfig) {
    this.config = config;
  }

  private async request<T>(path: string, options?: RequestInit): Promise<T> {
    const token = await this.config.getAccessToken();
    const response = await fetch(`${this.config.baseUrl}${path}`, {
      ...options,
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${token}`,
        'X-Tenant-ID': this.config.tenantId,
        ...(options?.headers ?? {}),
      },
    });

    if (!response.ok) {
      const error: ApiError = await response.json().catch(() => ({
        error: { code: 'UNKNOWN_ERROR', message: `HTTP ${response.status}` },
        meta: { requestId: '', timestamp: new Date().toISOString(), apiVersion: 'v1' },
      }));
      throw new SupportHubError(error.error.code, error.error.message, response.status);
    }

    const body: ApiResponse<T> = await response.json();
    return body.data;
  }

  // Ticket operations
  async createTicket(req: CreateTicketRequest): Promise<Ticket> {
    return this.request<Ticket>('/api/v1/tickets', {
      method: 'POST',
      body: JSON.stringify(req),
    });
  }

  async getTicket(ticketNumber: string): Promise<Ticket> {
    return this.request<Ticket>(`/api/v1/tickets/${ticketNumber}`);
  }

  async listMyTickets(cursor?: string, limit = 25): Promise<PaginatedResponse<Ticket>> {
    const params = new URLSearchParams({ limit: String(limit) });
    if (cursor) params.set('cursor', cursor);
    return this.request<PaginatedResponse<Ticket>>(`/api/v1/tickets/me?${params}`);
  }

  async addComment(ticketNumber: string, content: string): Promise<TicketActivity> {
    return this.request<TicketActivity>(`/api/v1/tickets/${ticketNumber}/activities`, {
      method: 'POST',
      body: JSON.stringify({ content, activityType: 'COMMENT' }),
    });
  }

  async getTicketActivities(ticketNumber: string): Promise<TicketActivity[]> {
    return this.request<TicketActivity[]>(`/api/v1/tickets/${ticketNumber}/activities`);
  }

  // FAQ operations
  async searchFaq(query: string, limit = 5): Promise<FaqSearchResult> {
    return this.request<FaqSearchResult>('/api/v1/faqs/search', {
      method: 'POST',
      body: JSON.stringify({ query, limit }),
    });
  }

  async listFaqs(categoryId?: string): Promise<FaqEntry[]> {
    const params = categoryId ? `?categoryId=${categoryId}` : '';
    return this.request<FaqEntry[]>(`/api/v1/faqs${params}`);
  }

  // Notification operations
  async getNotifications(cursor?: string): Promise<PaginatedResponse<Notification>> {
    const params = cursor ? `?cursor=${cursor}` : '';
    return this.request<PaginatedResponse<Notification>>(`/api/v1/notifications/me${params}`);
  }

  async getUnreadCount(): Promise<number> {
    const result = await this.request<{ count: number }>('/api/v1/notifications/me/unread-count');
    return result.count;
  }

  async markNotificationRead(notificationId: string): Promise<void> {
    await this.request<void>(`/api/v1/notifications/${notificationId}/read`, { method: 'PUT' });
  }

  // Profile operations
  async getProfile(): Promise<{ id: string; displayName: string; preferredLanguage: string }> {
    return this.request('/api/v1/customers/me');
  }

  async updateProfile(updates: { displayName?: string; preferredLanguage?: string }): Promise<void> {
    await this.request('/api/v1/customers/me', { method: 'PUT', body: JSON.stringify(updates) });
  }

  async getOrderHistory(): Promise<unknown[]> {
    return this.request('/api/v1/customers/me/orders');
  }
}
