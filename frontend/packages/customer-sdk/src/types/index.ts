export enum TicketStatus {
  OPEN = 'OPEN',
  IN_PROGRESS = 'IN_PROGRESS',
  WAITING_FOR_CUSTOMER = 'WAITING_FOR_CUSTOMER',
  RESOLVED = 'RESOLVED',
  CLOSED = 'CLOSED',
}

export enum TicketPriority {
  LOW = 'LOW',
  MEDIUM = 'MEDIUM',
  HIGH = 'HIGH',
  URGENT = 'URGENT',
}

export enum ActivityType {
  COMMENT = 'COMMENT',
  STATUS_CHANGE = 'STATUS_CHANGE',
  ASSIGNMENT = 'ASSIGNMENT',
  NOTE = 'NOTE',
}

export interface Customer {
  id: string;
  name: string;
  maskedPhone: string;
  tenantId: string;
  createdAt: string;
}

export interface Ticket {
  id: string;
  ticketNumber: string;
  title: string;
  description: string;
  status: TicketStatus;
  priority: TicketPriority;
  tenantId: string;
  customerId: string;
  assignedAgentId?: string;
  categoryId: string;
  subCategoryId?: string;
  orderId?: string;
  channel: string;
  createdAt: string;
  updatedAt: string;
  resolvedAt?: string;
}

export interface TicketActivity {
  id: string;
  ticketId: string;
  type: ActivityType;
  content: string;
  authorId: string;
  authorType: 'CUSTOMER' | 'AGENT' | 'SYSTEM';
  createdAt: string;
}

export interface FAQ {
  id: string;
  question: string;
  answer: string;
  categorySlug: string;
  tenantId: string;
  viewCount: number;
  helpful?: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface CreateTicketRequest {
  title: string;
  description: string;
  categoryId: string;
  subCategoryId?: string;
  orderId?: string;
  channel: string;
}

export interface AddActivityRequest {
  content: string;
  type: ActivityType;
}

export interface PaginatedResponse<T> {
  data: T[];
  pagination: {
    cursor?: string;
    hasMore: boolean;
    total?: number;
    limit: number;
  };
  meta: ResponseMeta;
}

export interface ApiResponse<T> {
  data: T;
  meta: ResponseMeta;
}

export interface ResponseMeta {
  requestId: string;
  timestamp: string;
  apiVersion: string;
}

export interface ApiError {
  error: {
    code: string;
    message: string;
    details?: Record<string, unknown>;
    traceId?: string;
  };
  meta: ResponseMeta;
}

export interface SupportHubClientConfig {
  baseUrl: string;
  tenantId: string;
  authToken?: string;
}
