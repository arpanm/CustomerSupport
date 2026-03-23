const BASE_URL = import.meta.env.VITE_API_BASE_URL as string;
const TENANT_ID = import.meta.env.VITE_TENANT_ID as string;

function authHeaders(token: string): Record<string, string> {
  return {
    'Content-Type': 'application/json',
    Authorization: `Bearer ${token}`,
    'X-Tenant-ID': TENANT_ID,
  };
}

async function api<T>(path: string, token: string, options?: RequestInit): Promise<T> {
  const res = await fetch(`${BASE_URL}${path}`, {
    ...options,
    headers: { ...authHeaders(token), ...(options?.headers ?? {}) },
  });
  if (!res.ok) throw new Error(`API error ${res.status}: ${path}`);
  const body = (await res.json()) as { data: T };
  return body.data;
}

// ---- Categories ----
export interface Category {
  id: string;
  name: string;
  slug: string;
  description: string;
  slaFirstResponseHours: number;
  slaResolutionHours: number;
  defaultPriority: string;
  isActive: boolean;
}

export interface CreateCategoryRequest {
  name: string;
  slug: string;
  description?: string;
  slaFirstResponseHours: number;
  slaResolutionHours: number;
  defaultPriority: string;
}

export const listCategories = (token: string): Promise<Category[]> =>
  api('/api/v1/categories', token);

export const createCategory = (token: string, req: CreateCategoryRequest): Promise<Category> =>
  api('/api/v1/categories', token, { method: 'POST', body: JSON.stringify(req) });

export const updateCategory = (
  token: string,
  id: string,
  req: Partial<CreateCategoryRequest>,
): Promise<Category> =>
  api(`/api/v1/categories/${id}`, token, { method: 'PUT', body: JSON.stringify(req) });

export const toggleCategory = (token: string, id: string, isActive: boolean): Promise<Category> =>
  api(`/api/v1/categories/${id}`, token, { method: 'PUT', body: JSON.stringify({ isActive }) });

// ---- Agents ----
export interface AgentUser {
  id: string;
  displayName: string;
  email: string;
  role: 'AGENT' | 'SENIOR_AGENT' | 'ADMIN' | 'SUPER_ADMIN';
  teamId?: string;
  isActive: boolean;
  isAvailable: boolean;
  createdAt: string;
}

export interface CreateAgentRequest {
  displayName: string;
  email: string;
  password: string;
  role: 'AGENT' | 'SENIOR_AGENT' | 'ADMIN';
  teamId?: string;
}

export const listAgents = (token: string): Promise<AgentUser[]> =>
  api('/api/v1/admin/agents', token);

export const createAgent = (token: string, req: CreateAgentRequest): Promise<AgentUser> =>
  api('/api/v1/admin/agents', token, { method: 'POST', body: JSON.stringify(req) });

export const updateAgent = (
  token: string,
  id: string,
  req: Partial<CreateAgentRequest & { isActive: boolean }>,
): Promise<AgentUser> =>
  api(`/api/v1/admin/agents/${id}`, token, { method: 'PUT', body: JSON.stringify(req) });

// ---- SLA Policies ----
export interface SlaPolicy {
  id: string;
  categoryId: string;
  priority: string;
  firstResponseHours: number;
  resolutionHours: number;
}

export interface CreateSlaPolicyRequest {
  categoryId: string;
  priority: string;
  firstResponseHours: number;
  resolutionHours: number;
}

export const listSlaPolicies = (token: string): Promise<SlaPolicy[]> =>
  api('/api/v1/admin/sla-policies', token);

export const createSlaPolicy = (token: string, req: CreateSlaPolicyRequest): Promise<SlaPolicy> =>
  api('/api/v1/admin/sla-policies', token, { method: 'POST', body: JSON.stringify(req) });

export const updateSlaPolicy = (
  token: string,
  id: string,
  req: Partial<CreateSlaPolicyRequest>,
): Promise<SlaPolicy> =>
  api(`/api/v1/admin/sla-policies/${id}`, token, { method: 'PUT', body: JSON.stringify(req) });

// ---- Reports ----
export interface DashboardSummary {
  totalTickets: number;
  openTickets: number;
  resolvedTickets: number;
  avgResolutionTimeMinutes: number;
  slaBreachCount: number;
  slaBreachRate: number;
}

export const getDashboardSummary = (token: string, from: string, to: string): Promise<DashboardSummary> =>
  api(`/api/v1/reports/dashboard?from=${from}&to=${to}`, token);
