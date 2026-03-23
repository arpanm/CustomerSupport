const BASE_URL = import.meta.env.VITE_API_BASE_URL as string;
const TENANT_ID = import.meta.env.VITE_TENANT_ID as string;

export interface TicketSummary {
  id: string;
  ticketNumber: string;
  title: string;
  status: string;
  priority: string;
  customerName: string;
  assignedAgentId: string | null;
  categoryName: string;
  createdAt: string;
  updatedAt: string;
  slaBreached: boolean;
  sentimentLabel?: string;
}

export interface TicketDetail extends TicketSummary {
  description: string;
  channel: string;
  customerId: string;
  subCategoryName?: string;
  orderId?: string;
  tags: string[];
  firstResponseDeadline?: string;
  resolutionDeadline?: string;
}

export interface TicketActivity {
  id: string;
  activityType: string;
  content: string;
  isInternal: boolean;
  actorId: string;
  actorType: 'CUSTOMER' | 'AGENT';
  actorName: string;
  createdAt: string;
}

export interface ResolutionSuggestion {
  title: string;
  content: string;
  confidence: number;
}

export interface TicketsPage {
  data: TicketSummary[];
  pagination: { cursor?: string; hasMore: boolean; total?: number; limit: number };
}

function authHeaders(token: string): Record<string, string> {
  return {
    'Content-Type': 'application/json',
    Authorization: `Bearer ${token}`,
    'X-Tenant-ID': TENANT_ID,
  };
}

export async function fetchTickets(
  token: string,
  params: {
    status?: string;
    priority?: string;
    assignedToMe?: boolean;
    search?: string;
    cursor?: string;
    limit?: number;
    agentId?: string;
  },
): Promise<TicketsPage> {
  const qs = new URLSearchParams();
  if (params.status) qs.set('status', params.status);
  if (params.priority) qs.set('priority', params.priority);
  if (params.assignedToMe && params.agentId) qs.set('assignedAgentId', params.agentId);
  if (params.search) qs.set('search', params.search);
  if (params.cursor) qs.set('cursor', params.cursor);
  qs.set('limit', String(params.limit ?? 25));
  qs.set('sort', 'createdAt');
  qs.set('direction', 'desc');

  const res = await fetch(`${BASE_URL}/api/v1/tickets?${qs.toString()}`, {
    headers: authHeaders(token),
  });
  if (!res.ok) throw new Error(`Failed to fetch tickets: ${res.status}`);
  const body = (await res.json()) as { data: TicketsPage };
  return body.data;
}

export async function fetchTicketDetail(token: string, ticketNumber: string): Promise<TicketDetail> {
  const res = await fetch(`${BASE_URL}/api/v1/tickets/${ticketNumber}`, {
    headers: authHeaders(token),
  });
  if (!res.ok) throw new Error(`Failed to fetch ticket: ${res.status}`);
  const body = (await res.json()) as { data: TicketDetail };
  return body.data;
}

export async function fetchActivities(token: string, ticketNumber: string): Promise<TicketActivity[]> {
  const res = await fetch(`${BASE_URL}/api/v1/tickets/${ticketNumber}/activities`, {
    headers: authHeaders(token),
  });
  if (!res.ok) throw new Error(`Failed to fetch activities: ${res.status}`);
  const body = (await res.json()) as { data: TicketActivity[] };
  return body.data;
}

export async function addComment(
  token: string,
  ticketNumber: string,
  content: string,
  isInternal: boolean,
): Promise<TicketActivity> {
  const res = await fetch(`${BASE_URL}/api/v1/tickets/${ticketNumber}/activities`, {
    method: 'POST',
    headers: authHeaders(token),
    body: JSON.stringify({ content, activityType: 'COMMENT', isInternal }),
  });
  if (!res.ok) throw new Error(`Failed to add comment: ${res.status}`);
  const body = (await res.json()) as { data: TicketActivity };
  return body.data;
}

export async function updateTicketStatus(
  token: string,
  ticketNumber: string,
  action: 'resolve' | 'escalate' | 'reopen',
  note?: string,
): Promise<TicketDetail> {
  const res = await fetch(`${BASE_URL}/api/v1/tickets/${ticketNumber}/actions/${action}`, {
    method: 'POST',
    headers: authHeaders(token),
    body: JSON.stringify({ note }),
  });
  if (!res.ok) throw new Error(`Failed to ${action} ticket: ${res.status}`);
  const body = (await res.json()) as { data: TicketDetail };
  return body.data;
}

export async function assignTicket(
  token: string,
  ticketNumber: string,
  agentId: string,
): Promise<TicketDetail> {
  const res = await fetch(`${BASE_URL}/api/v1/tickets/${ticketNumber}`, {
    method: 'PUT',
    headers: authHeaders(token),
    body: JSON.stringify({ assignedAgentId: agentId }),
  });
  if (!res.ok) throw new Error(`Failed to assign ticket: ${res.status}`);
  const body = (await res.json()) as { data: TicketDetail };
  return body.data;
}

export async function fetchAiSuggestions(
  token: string,
  ticketId: string,
  title: string,
  description: string,
  categorySlug: string,
): Promise<ResolutionSuggestion[]> {
  const res = await fetch(`${BASE_URL}/api/v1/ai/resolution-suggestions`, {
    method: 'POST',
    headers: authHeaders(token),
    body: JSON.stringify({ ticketId, title, description, categorySlug }),
  });
  if (!res.ok) return [];
  const body = (await res.json()) as { data: ResolutionSuggestion[] };
  return body.data ?? [];
}
