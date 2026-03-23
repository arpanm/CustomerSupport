import type {
  SupportHubClientConfig,
  Ticket,
  TicketActivity,
  FAQ,
  CreateTicketRequest,
  AddActivityRequest,
  ApiResponse,
  PaginatedResponse,
} from './types/index.js';

export class SupportHubClientError extends Error {
  constructor(
    public readonly code: string,
    message: string,
    public readonly status: number,
    public readonly traceId?: string,
  ) {
    super(message);
    this.name = 'SupportHubClientError';
  }
}

export class SupportHubClient {
  private readonly baseUrl: string;
  private readonly tenantId: string;
  private authToken?: string;

  constructor(config: SupportHubClientConfig) {
    this.baseUrl = config.baseUrl.replace(/\/$/, '');
    this.tenantId = config.tenantId;
    this.authToken = config.authToken;
  }

  setAuthToken(token: string): void {
    this.authToken = token;
  }

  clearAuthToken(): void {
    this.authToken = undefined;
  }

  private buildHeaders(): HeadersInit {
    const headers: Record<string, string> = {
      'Content-Type': 'application/json',
      'X-Tenant-ID': this.tenantId,
    };

    if (this.authToken !== undefined && this.authToken !== '') {
      headers['Authorization'] = `Bearer ${this.authToken}`;
    }

    return headers;
  }

  private async request<T>(path: string, init?: RequestInit): Promise<T> {
    const url = `${this.baseUrl}/api/v1${path}`;
    const response = await fetch(url, {
      ...init,
      headers: {
        ...this.buildHeaders(),
        ...(init?.headers ?? {}),
      },
    });

    if (!response.ok) {
      const errorBody = await response.json().catch(() => ({
        error: { code: 'UNKNOWN_ERROR', message: 'An unknown error occurred' },
      })) as { error: { code: string; message: string; traceId?: string } };

      throw new SupportHubClientError(
        errorBody.error.code,
        errorBody.error.message,
        response.status,
        errorBody.error.traceId,
      );
    }

    return response.json() as Promise<T>;
  }

  async createTicket(request: CreateTicketRequest): Promise<ApiResponse<Ticket>> {
    return this.request<ApiResponse<Ticket>>('/tickets', {
      method: 'POST',
      body: JSON.stringify(request),
    });
  }

  async getTickets(params?: {
    cursor?: string;
    limit?: number;
    status?: string;
  }): Promise<PaginatedResponse<Ticket>> {
    const searchParams = new URLSearchParams();
    if (params?.cursor !== undefined && params.cursor !== '') {
      searchParams.set('cursor', params.cursor);
    }
    if (params?.limit !== undefined) {
      searchParams.set('limit', String(params.limit));
    }
    if (params?.status !== undefined && params.status !== '') {
      searchParams.set('status', params.status);
    }

    const query = searchParams.toString();
    const path = query !== '' ? `/tickets?${query}` : '/tickets';
    return this.request<PaginatedResponse<Ticket>>(path);
  }

  async getTicket(ticketNumber: string): Promise<ApiResponse<Ticket>> {
    return this.request<ApiResponse<Ticket>>(`/tickets/${ticketNumber}`);
  }

  async addActivity(
    ticketId: string,
    request: AddActivityRequest,
  ): Promise<ApiResponse<TicketActivity>> {
    return this.request<ApiResponse<TicketActivity>>(`/tickets/${ticketId}/activities`, {
      method: 'POST',
      body: JSON.stringify(request),
    });
  }

  async getActivities(
    ticketId: string,
    params?: { cursor?: string; limit?: number },
  ): Promise<PaginatedResponse<TicketActivity>> {
    const searchParams = new URLSearchParams();
    if (params?.cursor !== undefined && params.cursor !== '') {
      searchParams.set('cursor', params.cursor);
    }
    if (params?.limit !== undefined) {
      searchParams.set('limit', String(params.limit));
    }

    const query = searchParams.toString();
    const path =
      query !== ''
        ? `/tickets/${ticketId}/activities?${query}`
        : `/tickets/${ticketId}/activities`;
    return this.request<PaginatedResponse<TicketActivity>>(path);
  }

  async getFaqs(params?: {
    query?: string;
    categorySlug?: string;
    cursor?: string;
    limit?: number;
  }): Promise<PaginatedResponse<FAQ>> {
    const searchParams = new URLSearchParams();
    if (params?.query !== undefined && params.query !== '') {
      searchParams.set('query', params.query);
    }
    if (params?.categorySlug !== undefined && params.categorySlug !== '') {
      searchParams.set('categorySlug', params.categorySlug);
    }
    if (params?.cursor !== undefined && params.cursor !== '') {
      searchParams.set('cursor', params.cursor);
    }
    if (params?.limit !== undefined) {
      searchParams.set('limit', String(params.limit));
    }

    const query = searchParams.toString();
    const path = query !== '' ? `/faqs?${query}` : '/faqs';
    return this.request<PaginatedResponse<FAQ>>(path);
  }
}
