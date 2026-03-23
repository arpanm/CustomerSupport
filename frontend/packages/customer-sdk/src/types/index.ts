export type TicketStatus =
  | 'OPEN' | 'PENDING_AGENT_RESPONSE' | 'PENDING_CUSTOMER_RESPONSE'
  | 'IN_PROGRESS' | 'ESCALATED' | 'RESOLVED' | 'CLOSED' | 'REOPENED';

export type Priority = 'LOW' | 'MEDIUM' | 'HIGH' | 'URGENT';
export type Channel = 'web' | 'mobile' | 'whatsapp' | 'email';

export interface Ticket {
  id: string;
  ticketNumber: string;
  title: string;
  description: string;
  status: TicketStatus;
  priority: Priority;
  channel: Channel;
  categoryId: string;
  customerId: string;
  createdAt: string;
  updatedAt: string;
  resolvedAt?: string;
  slaBreached: boolean;
}

export interface TicketActivity {
  id: string;
  ticketId: string;
  activityType: 'COMMENT' | 'STATUS_CHANGE' | 'ASSIGNMENT' | 'RESOLUTION';
  content: string;
  isInternal: boolean;
  actorId: string;
  actorType: 'CUSTOMER' | 'AGENT';
  createdAt: string;
}

export interface FaqEntry {
  id: string;
  question: string;
  answer: string;
  categoryId?: string;
  tags: string[];
}

export interface FaqSearchResult {
  results: Array<{ id: string; question: string; answerExcerpt: string; score: number }>;
}

export interface Notification {
  id: string;
  channel: 'SMS' | 'EMAIL' | 'IN_APP' | 'WHATSAPP';
  subject: string;
  content: string;
  status: 'PENDING' | 'SENT' | 'DELIVERED' | 'FAILED';
  createdAt: string;
}

export interface CreateTicketRequest {
  title: string;
  description: string;
  categoryId: string;
  subCategoryId?: string;
  orderId?: string;
  channel?: Channel;
}

export interface PaginatedResponse<T> {
  data: T[];
  cursor?: string;
  hasMore: boolean;
  total?: number;
}

export interface ApiResponse<T> {
  data: T;
  meta: { requestId: string; timestamp: string; apiVersion: string };
}

export interface ApiError {
  error: { code: string; message: string; details?: Record<string, unknown>; traceId?: string };
  meta: { requestId: string; timestamp: string; apiVersion: string };
}

export class SupportHubError extends Error {
  constructor(
    public readonly code: string,
    message: string,
    public readonly statusCode?: number
  ) {
    super(message);
    this.name = 'SupportHubError';
  }
}
