import {
  MOCK_TICKETS, MOCK_TICKET_DETAILS, MOCK_ACTIVITIES, MOCK_SUGGESTIONS,
  MOCK_METRICS, MOCK_NOTIFICATIONS, MOCK_CUSTOMER_PROFILES, MOCK_CATEGORIES,
} from './mockData.js';

const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080';
const TENANT_ID = import.meta.env.VITE_TENANT_ID ?? 'demo-tenant';

export interface Category { id: string; name: string; slug: string; }

export interface TicketSummary {
  id: string; ticketNumber: string; title: string; status: string; priority: string;
  customerName: string; assignedAgentId: string | null; categoryName: string;
  createdAt: string; updatedAt: string; slaBreached: boolean;
  sentimentLabel?: string; channel: string; tags: string[];
}

export interface TicketDetail extends TicketSummary {
  description: string; customerId: string; subCategoryName?: string; orderId?: string;
  firstResponseDeadline?: string; resolutionDeadline?: string;
  customerPhone?: string; customerEmail?: string | null; totalTickets?: number;
}

export interface TicketActivity {
  id: string; activityType: string; content: string; isInternal: boolean;
  actorId: string; actorType: 'CUSTOMER' | 'AGENT'; actorName: string; createdAt: string;
}

export interface ResolutionSuggestion { title: string; content: string; confidence: number; }

export interface TicketsPage {
  data: TicketSummary[];
  pagination: { cursor?: string; hasMore: boolean; total?: number; limit: number };
}

export interface DashboardMetrics {
  myOpenTickets: number; myUrgentTickets: number; unassignedTickets: number;
  slaBreachedTickets: number; resolvedToday: number; avgFirstResponseMinutes: number;
  teamOpenTickets: number; teamResolvedToday: number; myTicketsThisWeek: number; satisfactionScore: number;
}

export interface Notification {
  id: string; type: 'SLA_BREACH' | 'SLA_WARNING' | 'TICKET_ASSIGNED' | 'CUSTOMER_REPLY' | 'TICKET_ESCALATED';
  title: string; message: string; ticketNumber?: string; createdAt: string; isRead: boolean;
}

export interface CustomerProfile {
  id: string; name: string; phone: string; email: string | null;
  totalTickets: number; openTickets: number; joinedAt: string; preferredLanguage: string; lastOrderAt?: string;
}

function authHeaders(token: string): Record<string, string> {
  return { 'Content-Type': 'application/json', Authorization: `Bearer ${token}`, 'X-Tenant-ID': TENANT_ID };
}

async function apiFetch<T>(url: string, options?: RequestInit): Promise<T> {
  const res = await fetch(url, options);
  if (!res.ok) throw new Error(`API ${res.status}`);
  const body = (await res.json()) as { data: T };
  return body.data;
}

export async function fetchTickets(token: string, params: {
  status?: string; priority?: string; assignedToMe?: boolean; unassigned?: boolean;
  search?: string; cursor?: string; limit?: number; agentId?: string; tab?: string;
}): Promise<TicketsPage> {
  try {
    const qs = new URLSearchParams();
    if (params.status) qs.set('status', params.status);
    if (params.priority) qs.set('priority', params.priority);
    if (params.assignedToMe && params.agentId) qs.set('assignedAgentId', params.agentId);
    if (params.unassigned) qs.set('unassigned', 'true');
    if (params.search) qs.set('search', params.search);
    if (params.cursor) qs.set('cursor', params.cursor);
    qs.set('limit', String(params.limit ?? 25));
    return await apiFetch<TicketsPage>(`${BASE_URL}/api/v1/tickets?${qs}`, { headers: authHeaders(token) });
  } catch {
    let f = [...MOCK_TICKETS];
    const { tab, status, priority, search, agentId, assignedToMe, unassigned } = params;
    if (tab === 'mine') f = f.filter(t => t.assignedAgentId === 'agent-me');
    else if (tab === 'unassigned') f = f.filter(t => t.assignedAgentId == null);
    else if (tab === 'escalated') f = f.filter(t => t.status === 'ESCALATED');
    else if (tab === 'resolved') f = f.filter(t => ['RESOLVED', 'CLOSED'].includes(t.status));
    if (status) f = f.filter(t => t.status === status);
    if (priority) f = f.filter(t => t.priority === priority);
    if (assignedToMe && agentId) f = f.filter(t => t.assignedAgentId === 'agent-me');
    if (unassigned) f = f.filter(t => t.assignedAgentId == null);
    if (search) { const q = search.toLowerCase(); f = f.filter(t => t.title.toLowerCase().includes(q) || t.ticketNumber.toLowerCase().includes(q) || t.customerName.toLowerCase().includes(q)); }
    return { data: f, pagination: { hasMore: false, total: f.length, limit: 25 } };
  }
}

export async function fetchTicketDetail(token: string, ticketNumber: string): Promise<TicketDetail> {
  try {
    return await apiFetch<TicketDetail>(`${BASE_URL}/api/v1/tickets/${ticketNumber}`, { headers: authHeaders(token) });
  } catch {
    const mock = MOCK_TICKET_DETAILS[ticketNumber];
    if (mock) return mock;
    const s = MOCK_TICKETS.find(t => t.ticketNumber === ticketNumber);
    if (s) return { ...s, description: 'Customer reported an issue with their recent order.', customerId: 'cust-unknown' };
    throw new Error(`Ticket ${ticketNumber} not found`);
  }
}

export async function fetchActivities(token: string, ticketNumber: string): Promise<TicketActivity[]> {
  try {
    return await apiFetch<TicketActivity[]>(`${BASE_URL}/api/v1/tickets/${ticketNumber}/activities`, { headers: authHeaders(token) });
  } catch {
    return MOCK_ACTIVITIES[ticketNumber] ?? [];
  }
}

export async function addComment(token: string, ticketNumber: string, content: string, isInternal: boolean): Promise<TicketActivity> {
  try {
    return await apiFetch<TicketActivity>(`${BASE_URL}/api/v1/tickets/${ticketNumber}/activities`, {
      method: 'POST', headers: authHeaders(token), body: JSON.stringify({ content, activityType: 'COMMENT', isInternal }),
    });
  } catch {
    return { id: `act-${Date.now()}`, activityType: 'COMMENT', content, isInternal, actorId: 'agent-me', actorType: 'AGENT', actorName: 'You', createdAt: new Date().toISOString() };
  }
}

export async function updateTicketStatus(token: string, ticketNumber: string, action: 'resolve' | 'escalate' | 'reopen' | 'close', note?: string): Promise<TicketDetail> {
  try {
    return await apiFetch<TicketDetail>(`${BASE_URL}/api/v1/tickets/${ticketNumber}/actions/${action}`, {
      method: 'POST', headers: authHeaders(token), body: JSON.stringify({ note }),
    });
  } catch {
    const d = await fetchTicketDetail(token, ticketNumber);
    const m: Record<string, string> = { resolve: 'RESOLVED', escalate: 'ESCALATED', reopen: 'OPEN', close: 'CLOSED' };
    return { ...d, status: m[action] ?? d.status };
  }
}

export async function assignTicket(token: string, ticketNumber: string, agentId: string): Promise<TicketDetail> {
  try {
    return await apiFetch<TicketDetail>(`${BASE_URL}/api/v1/tickets/${ticketNumber}`, {
      method: 'PUT', headers: authHeaders(token), body: JSON.stringify({ assignedAgentId: agentId }),
    });
  } catch {
    return { ...(await fetchTicketDetail(token, ticketNumber)), assignedAgentId: agentId };
  }
}

export async function fetchAiSuggestions(token: string, ticketId: string, title: string, description: string, categorySlug: string): Promise<ResolutionSuggestion[]> {
  try {
    return await apiFetch<ResolutionSuggestion[]>(`${BASE_URL}/api/v1/ai/resolution-suggestions`, {
      method: 'POST', headers: authHeaders(token), body: JSON.stringify({ ticketId, title, description, categorySlug }),
    });
  } catch { return MOCK_SUGGESTIONS; }
}

export async function fetchDashboardMetrics(token: string): Promise<DashboardMetrics> {
  try { return await apiFetch<DashboardMetrics>(`${BASE_URL}/api/v1/reporting/agent/me/metrics`, { headers: authHeaders(token) }); }
  catch { return MOCK_METRICS; }
}

export async function fetchNotifications(token: string): Promise<Notification[]> {
  try { return await apiFetch<Notification[]>(`${BASE_URL}/api/v1/notifications`, { headers: authHeaders(token) }); }
  catch { return MOCK_NOTIFICATIONS; }
}

export async function markNotificationRead(token: string, id: string): Promise<void> {
  try { await fetch(`${BASE_URL}/api/v1/notifications/${id}/read`, { method: 'POST', headers: authHeaders(token) }); }
  catch { /* ignore */ }
}

export async function fetchCustomerProfile(token: string, customerId: string): Promise<CustomerProfile> {
  try { return await apiFetch<CustomerProfile>(`${BASE_URL}/api/v1/customers/${customerId}`, { headers: authHeaders(token) }); }
  catch { return MOCK_CUSTOMER_PROFILES[customerId] ?? { id: customerId, name: 'Unknown', phone: '—', email: null, totalTickets: 1, openTickets: 1, joinedAt: new Date().toISOString(), preferredLanguage: 'en' }; }
}

export async function fetchCategories(token: string): Promise<Category[]> {
  try { return await apiFetch<Category[]>(`${BASE_URL}/api/v1/categories`, { headers: authHeaders(token) }); }
  catch { return MOCK_CATEGORIES; }
}

export async function fetchRelatedTickets(token: string, customerId: string, excludeTicketNumber: string): Promise<TicketSummary[]> {
  try { return await apiFetch<TicketSummary[]>(`${BASE_URL}/api/v1/tickets?customerId=${customerId}&limit=3`, { headers: authHeaders(token) }); }
  catch { return MOCK_TICKETS.filter(t => t.ticketNumber !== excludeTicketNumber).slice(0, 3); }
}
