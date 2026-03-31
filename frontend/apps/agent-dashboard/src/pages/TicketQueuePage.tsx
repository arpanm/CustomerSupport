import { useState, useEffect } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { Link, useSearchParams } from 'react-router-dom';
import { useAuthStore } from '../store/authStore.js';
import { useWebSocketStore } from '../store/websocketStore.js';
import { fetchTickets, assignTicket, updateTicketStatus } from '../api/ticketApi.js';
import type { TicketSummary } from '../api/ticketApi.js';

function Icon({ d, className = 'h-4 w-4' }: { d: string; className?: string }) {
  return (
    <svg className={className} fill="none" viewBox="0 0 24 24" strokeWidth={1.5} stroke="currentColor">
      <path strokeLinecap="round" strokeLinejoin="round" d={d} />
    </svg>
  );
}

const ICONS = {
  search: 'M21 21l-5.197-5.197m0 0A7.5 7.5 0 105.196 5.196a7.5 7.5 0 0010.607 10.607z',
  filter: 'M12 3c2.755 0 5.455.232 8.083.678.533.09.917.556.917 1.096v1.044a2.25 2.25 0 01-.659 1.591l-5.432 5.432a2.25 2.25 0 00-.659 1.591v2.927a2.25 2.25 0 01-1.244 2.013L9.75 21v-6.568a2.25 2.25 0 00-.659-1.591L3.659 7.409A2.25 2.25 0 013 5.818V4.774c0-.54.384-1.006.917-1.096A48.32 48.32 0 0112 3z',
  x: 'M6 18L18 6M6 6l12 12',
  check: 'M4.5 12.75l6 6 9-13.5',
  assign: 'M15.75 6a3.75 3.75 0 11-7.5 0 3.75 3.75 0 017.5 0zM4.501 20.118a7.5 7.5 0 0114.998 0A17.933 17.933 0 0112 21.75c-2.676 0-5.216-.584-7.499-1.632z',
  resolve: 'M9 12.75L11.25 15 15 9.75M21 12a9 9 0 11-18 0 9 9 0 0118 0z',
  sort: 'M3 7.5L7.5 3m0 0L12 7.5M7.5 3v13.5m13.5 0L16.5 21m0 0L12 16.5m4.5 4.5V7.5',
  refresh: 'M16.023 9.348h4.992v-.001M2.985 19.644v-4.992m0 0h4.992m-4.993 0l3.181 3.183a8.25 8.25 0 0013.803-3.7M4.031 9.865a8.25 8.25 0 0113.803-3.7l3.181 3.182m0-4.991v4.99',
};

const PRIORITY_META: Record<string, { label: string; cls: string; dot: string }> = {
  URGENT: { label: 'Urgent', cls: 'bg-red-100 text-red-700 border border-red-200', dot: 'bg-red-500' },
  HIGH: { label: 'High', cls: 'bg-orange-100 text-orange-700 border border-orange-200', dot: 'bg-orange-500' },
  MEDIUM: { label: 'Medium', cls: 'bg-yellow-100 text-yellow-700 border border-yellow-200', dot: 'bg-yellow-500' },
  LOW: { label: 'Low', cls: 'bg-gray-100 text-gray-600 border border-gray-200', dot: 'bg-gray-400' },
};

const STATUS_META: Record<string, { label: string; cls: string }> = {
  OPEN: { label: 'Open', cls: 'bg-blue-100 text-blue-700' },
  IN_PROGRESS: { label: 'In Progress', cls: 'bg-indigo-100 text-indigo-700' },
  ESCALATED: { label: 'Escalated', cls: 'bg-red-100 text-red-700' },
  PENDING_CUSTOMER_RESPONSE: { label: 'Pending Customer', cls: 'bg-yellow-100 text-yellow-700' },
  PENDING_AGENT_RESPONSE: { label: 'Pending Agent', cls: 'bg-orange-100 text-orange-700' },
  RESOLVED: { label: 'Resolved', cls: 'bg-green-100 text-green-700' },
  CLOSED: { label: 'Closed', cls: 'bg-gray-100 text-gray-600' },
  REOPENED: { label: 'Reopened', cls: 'bg-purple-100 text-purple-700' },
};

const SENTIMENT_EMOJI: Record<string, { emoji: string; label: string }> = {
  very_negative: { emoji: '😡', label: 'Very Negative' },
  negative: { emoji: '😞', label: 'Negative' },
  neutral: { emoji: '😐', label: 'Neutral' },
  positive: { emoji: '😊', label: 'Positive' },
  very_positive: { emoji: '😄', label: 'Very Positive' },
};

const CHANNEL_ICON: Record<string, string> = {
  WEB: '🌐', APP: '📱', WHATSAPP: '💬', SMS: '📨', EMAIL: '✉️',
};

const TABS = [
  { key: 'all', label: 'All Tickets' },
  { key: 'mine', label: 'My Tickets' },
  { key: 'unassigned', label: 'Unassigned' },
  { key: 'escalated', label: 'Escalated' },
  { key: 'resolved', label: 'Resolved' },
];

function timeAgo(iso: string): string {
  const diff = Date.now() - new Date(iso).getTime();
  const m = Math.floor(diff / 60000);
  if (m < 1) return 'just now';
  if (m < 60) return `${m}m ago`;
  const h = Math.floor(m / 60);
  if (h < 24) return `${h}h ago`;
  return `${Math.floor(h / 24)}d ago`;
}

function TicketRow({ ticket, selected, onSelect, onAssignToMe, onResolve }: {
  ticket: TicketSummary;
  selected: boolean;
  onSelect: (id: string) => void;
  onAssignToMe: (t: TicketSummary) => void;
  onResolve: (t: TicketSummary) => void;
}) {
  const priorityMeta = PRIORITY_META[ticket.priority] ?? PRIORITY_META.LOW;
  const statusMeta = STATUS_META[ticket.status] ?? { label: ticket.status, cls: 'bg-gray-100 text-gray-600' };
  const sentiment = ticket.sentimentLabel ? SENTIMENT_EMOJI[ticket.sentimentLabel] : null;
  const canResolve = ['OPEN', 'IN_PROGRESS', 'PENDING_CUSTOMER_RESPONSE', 'PENDING_AGENT_RESPONSE'].includes(ticket.status);

  return (
    <div className={`group flex items-center gap-3 border-b border-gray-100 px-4 py-3 transition-colors hover:bg-blue-50/30 ${selected ? 'bg-blue-50' : ''} ${ticket.slaBreached ? 'border-l-2 border-l-red-400' : ''}`}>
      <input
        type="checkbox"
        checked={selected}
        onChange={() => { onSelect(ticket.id); }}
        onClick={e => { e.stopPropagation(); }}
        className="h-4 w-4 rounded border-gray-300 text-blue-600 focus:ring-blue-500"
      />

      <Link to={`/tickets/${ticket.ticketNumber}`} className="flex min-w-0 flex-1 items-center gap-3">
        {/* Priority dot */}
        <span className={`h-2 w-2 shrink-0 rounded-full ${priorityMeta.dot}`} title={priorityMeta.label} />

        {/* Main info */}
        <div className="min-w-0 flex-1">
          <div className="flex items-center gap-2">
            <p className="truncate text-sm font-medium text-gray-900 group-hover:text-blue-700">{ticket.title}</p>
            {ticket.slaBreached && <span className="shrink-0 rounded-full bg-red-100 px-1.5 py-0.5 text-[10px] font-semibold text-red-700">SLA</span>}
          </div>
          <div className="mt-0.5 flex items-center gap-2 text-xs text-gray-400">
            <span className="font-mono">{ticket.ticketNumber}</span>
            <span>•</span>
            <span>{ticket.customerName}</span>
            <span>•</span>
            <span>{ticket.categoryName}</span>
            {ticket.channel && <><span>•</span><span>{CHANNEL_ICON[ticket.channel] ?? ''}  {ticket.channel}</span></>}
          </div>
        </div>

        {/* Tags */}
        {ticket.tags.length > 0 && (
          <div className="hidden items-center gap-1 lg:flex">
            {ticket.tags.slice(0, 2).map(tag => (
              <span key={tag} className="rounded-full bg-gray-100 px-2 py-0.5 text-[10px] text-gray-500">{tag}</span>
            ))}
          </div>
        )}

        {/* Sentiment */}
        {sentiment && (
          <span title={sentiment.label} className="shrink-0 text-base">{sentiment.emoji}</span>
        )}

        {/* Priority badge */}
        <span className={`hidden shrink-0 rounded-full px-2 py-0.5 text-[10px] font-medium sm:inline-block ${priorityMeta.cls}`}>{priorityMeta.label}</span>

        {/* Status badge */}
        <span className={`shrink-0 rounded-md px-2 py-0.5 text-[10px] font-medium ${statusMeta.cls}`}>{statusMeta.label}</span>

        {/* Time */}
        <span className="hidden shrink-0 text-xs text-gray-400 lg:block">{timeAgo(ticket.updatedAt)}</span>
      </Link>

      {/* Quick actions (visible on hover) */}
      <div className="flex shrink-0 items-center gap-1 opacity-0 transition-opacity group-hover:opacity-100">
        {ticket.assignedAgentId == null && (
          <button
            onClick={(e) => { e.preventDefault(); onAssignToMe(ticket); }}
            className="rounded-lg bg-blue-50 px-2 py-1 text-xs font-medium text-blue-700 hover:bg-blue-100"
            title="Assign to me"
          >
            Claim
          </button>
        )}
        {canResolve && (
          <button
            onClick={(e) => { e.preventDefault(); onResolve(ticket); }}
            className="rounded-lg bg-green-50 px-2 py-1 text-xs font-medium text-green-700 hover:bg-green-100"
            title="Resolve ticket"
          >
            Resolve
          </button>
        )}
      </div>
    </div>
  );
}

export function TicketQueuePage() {
  const { token, user } = useAuthStore();
  const { lastTicketUpdate } = useWebSocketStore();
  const queryClient = useQueryClient();
  const [searchParams, setSearchParams] = useSearchParams();

  const [tab, setTab] = useState(searchParams.get('tab') ?? 'all');
  const [search, setSearch] = useState('');
  const [statusFilter, setStatusFilter] = useState('');
  const [priorityFilter, setPriorityFilter] = useState('');
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [showFilters, setShowFilters] = useState(false);

  const safeToken = token ?? '';

  // Sync tab to URL
  useEffect(() => {
    setSearchParams(prev => { if (tab !== 'all') prev.set('tab', tab); else prev.delete('tab'); return prev; }, { replace: true });
  }, [tab, setSearchParams]);

  // Refresh on WS events
  useEffect(() => {
    if (lastTicketUpdate) void queryClient.invalidateQueries({ queryKey: ['tickets'] });
  }, [lastTicketUpdate, queryClient]);

  const queryParams = {
    tab,
    status: statusFilter || undefined,
    priority: priorityFilter || undefined,
    search: search || undefined,
    agentId: user?.id,
    limit: 50,
  };

  const { data, isLoading, isRefetching, refetch } = useQuery({
    queryKey: ['tickets', 'queue', tab, statusFilter, priorityFilter, search],
    queryFn: () => fetchTickets(safeToken, queryParams),
    enabled: !!token,
    staleTime: 30_000,
    refetchInterval: 60_000,
  });

  const assignMutation = useMutation({
    mutationFn: (ticket: TicketSummary) => assignTicket(safeToken, ticket.ticketNumber, user?.id ?? ''),
    onSuccess: () => { void queryClient.invalidateQueries({ queryKey: ['tickets'] }); },
  });

  const resolveMutation = useMutation({
    mutationFn: (ticket: TicketSummary) => updateTicketStatus(safeToken, ticket.ticketNumber, 'resolve'),
    onSuccess: () => { void queryClient.invalidateQueries({ queryKey: ['tickets'] }); },
  });

  const bulkAssignMutation = useMutation({
    mutationFn: async (ids: string[]) => {
      const tickets = (data?.data ?? []).filter(t => ids.includes(t.id));
      await Promise.all(tickets.map(t => assignTicket(safeToken, t.ticketNumber, user?.id ?? '')));
    },
    onSuccess: () => { setSelected(new Set()); void queryClient.invalidateQueries({ queryKey: ['tickets'] }); },
  });

  const bulkResolveMutation = useMutation({
    mutationFn: async (ids: string[]) => {
      const tickets = (data?.data ?? []).filter(t => ids.includes(t.id));
      await Promise.all(tickets.map(t => updateTicketStatus(safeToken, t.ticketNumber, 'resolve')));
    },
    onSuccess: () => { setSelected(new Set()); void queryClient.invalidateQueries({ queryKey: ['tickets'] }); },
  });

  const tickets = data?.data ?? [];
  const total = data?.pagination.total ?? tickets.length;
  const allSelected = tickets.length > 0 && selected.size === tickets.length;

  const toggleSelect = (id: string) => {
    setSelected(prev => { const s = new Set(prev); if (s.has(id)) s.delete(id); else s.add(id); return s; });
  };

  const toggleAll = () => {
    setSelected(allSelected ? new Set() : new Set(tickets.map(t => t.id)));
  };

  const clearFilters = () => { setSearch(''); setStatusFilter(''); setPriorityFilter(''); };
  const hasFilters = search || statusFilter || priorityFilter;

  const tabCounts: Record<string, number> = {
    all: tickets.length,
    mine: tickets.filter(t => t.assignedAgentId === 'agent-me').length,
    unassigned: tickets.filter(t => !t.assignedAgentId).length,
    escalated: tickets.filter(t => t.status === 'ESCALATED').length,
    resolved: tickets.filter(t => ['RESOLVED', 'CLOSED'].includes(t.status)).length,
  };

  return (
    <div className="flex h-full flex-col">
      {/* ── Header ── */}
      <div className="border-b border-gray-200 bg-white px-6 pb-0 pt-5">
        <div className="flex items-center justify-between pb-4">
          <div>
            <h1 className="text-xl font-bold text-gray-900">Ticket Queue</h1>
            <p className="text-sm text-gray-500">{total} ticket{total !== 1 ? 's' : ''}{isRefetching ? ' · refreshing…' : ''}</p>
          </div>
          <div className="flex items-center gap-2">
            <button onClick={() => { void refetch(); }} className="rounded-lg border border-gray-200 p-2 text-gray-500 hover:bg-gray-50" title="Refresh">
              <Icon d={ICONS.refresh} className={`h-4 w-4 ${isRefetching ? 'animate-spin' : ''}`} />
            </button>
            <button onClick={() => { setShowFilters(v => !v); }} className={`flex items-center gap-1.5 rounded-lg border px-3 py-2 text-sm ${showFilters ? 'border-blue-300 bg-blue-50 text-blue-700' : 'border-gray-200 text-gray-600 hover:bg-gray-50'}`}>
              <Icon d={ICONS.filter} />Filters{hasFilters ? ' ●' : ''}
            </button>
          </div>
        </div>

        {/* Tabs */}
        <div className="flex gap-0 border-t border-gray-100">
          {TABS.map(t => (
            <button
              key={t.key}
              onClick={() => { setTab(t.key); setSelected(new Set()); }}
              className={`flex items-center gap-1.5 border-b-2 px-4 py-2.5 text-sm font-medium transition-colors ${tab === t.key ? 'border-blue-600 text-blue-600' : 'border-transparent text-gray-500 hover:text-gray-900'}`}
            >
              {t.label}
              {tabCounts[t.key] != null && tabCounts[t.key] > 0 && (
                <span className={`rounded-full px-1.5 py-0.5 text-[10px] font-semibold ${tab === t.key ? 'bg-blue-100 text-blue-700' : 'bg-gray-100 text-gray-500'}`}>{tabCounts[t.key]}</span>
              )}
            </button>
          ))}
        </div>
      </div>

      {/* ── Filter Panel ── */}
      {showFilters && (
        <div className="border-b border-gray-200 bg-gray-50 px-6 py-3">
          <div className="flex flex-wrap items-center gap-3">
            <div className="relative">
              <Icon d={ICONS.search} className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-gray-400" />
              <input
                type="text"
                placeholder="Search tickets..."
                value={search}
                onChange={e => { setSearch(e.target.value); }}
                className="w-56 rounded-lg border border-gray-200 bg-white py-2 pl-9 pr-3 text-sm focus:border-blue-400 focus:outline-none focus:ring-1 focus:ring-blue-400"
              />
            </div>
            <select value={statusFilter} onChange={e => { setStatusFilter(e.target.value); }} className="rounded-lg border border-gray-200 bg-white px-3 py-2 text-sm focus:border-blue-400 focus:outline-none">
              <option value="">All Statuses</option>
              <option value="OPEN">Open</option>
              <option value="IN_PROGRESS">In Progress</option>
              <option value="ESCALATED">Escalated</option>
              <option value="PENDING_CUSTOMER_RESPONSE">Pending Customer</option>
              <option value="RESOLVED">Resolved</option>
            </select>
            <select value={priorityFilter} onChange={e => { setPriorityFilter(e.target.value); }} className="rounded-lg border border-gray-200 bg-white px-3 py-2 text-sm focus:border-blue-400 focus:outline-none">
              <option value="">All Priorities</option>
              <option value="URGENT">Urgent</option>
              <option value="HIGH">High</option>
              <option value="MEDIUM">Medium</option>
              <option value="LOW">Low</option>
            </select>
            {hasFilters && (
              <button onClick={clearFilters} className="flex items-center gap-1 text-sm text-red-500 hover:text-red-700">
                <Icon d={ICONS.x} className="h-3.5 w-3.5" />Clear
              </button>
            )}
          </div>
        </div>
      )}

      {/* ── Bulk Action Bar ── */}
      {selected.size > 0 && (
        <div className="flex items-center gap-3 border-b border-blue-200 bg-blue-50 px-6 py-2.5">
          <span className="text-sm font-medium text-blue-800">{selected.size} selected</span>
          <div className="h-4 w-px bg-blue-200" />
          <button onClick={() => { bulkAssignMutation.mutate(Array.from(selected)); }} disabled={bulkAssignMutation.isPending} className="flex items-center gap-1.5 rounded-lg bg-blue-600 px-3 py-1.5 text-xs font-medium text-white hover:bg-blue-700 disabled:opacity-50">
            <Icon d={ICONS.assign} className="h-3.5 w-3.5" />Assign to me
          </button>
          <button onClick={() => { bulkResolveMutation.mutate(Array.from(selected)); }} disabled={bulkResolveMutation.isPending} className="flex items-center gap-1.5 rounded-lg bg-green-600 px-3 py-1.5 text-xs font-medium text-white hover:bg-green-700 disabled:opacity-50">
            <Icon d={ICONS.resolve} className="h-3.5 w-3.5" />Resolve all
          </button>
          <button onClick={() => { setSelected(new Set()); }} className="text-xs text-blue-600 hover:underline">Cancel</button>
        </div>
      )}

      {/* ── Table Header ── */}
      {tickets.length > 0 && (
        <div className="flex items-center gap-3 border-b border-gray-200 bg-gray-50 px-4 py-2">
          <input type="checkbox" checked={allSelected} onChange={toggleAll} className="h-4 w-4 rounded border-gray-300 text-blue-600 focus:ring-blue-500" />
          <div className="flex-1 text-xs font-medium uppercase tracking-wide text-gray-400">Ticket</div>
          <div className="hidden w-20 text-right text-xs font-medium uppercase tracking-wide text-gray-400 lg:block">Priority</div>
          <div className="w-28 text-right text-xs font-medium uppercase tracking-wide text-gray-400">Status</div>
          <div className="hidden w-16 text-right text-xs font-medium uppercase tracking-wide text-gray-400 lg:block">Updated</div>
          <div className="w-24" />
        </div>
      )}

      {/* ── Ticket List ── */}
      <div className="flex-1 overflow-y-auto bg-white">
        {isLoading ? (
          <div className="space-y-0">
            {Array.from({ length: 8 }).map((_, i) => (
              <div key={i} className="h-16 animate-pulse border-b border-gray-100 bg-gray-50" style={{ animationDelay: `${i * 50}ms` }} />
            ))}
          </div>
        ) : tickets.length === 0 ? (
          <div className="flex h-64 flex-col items-center justify-center gap-3 text-gray-400">
            <svg className="h-12 w-12 text-gray-200" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1} d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
            </svg>
            <div className="text-center">
              <p className="font-medium text-gray-500">No tickets found</p>
              <p className="text-sm">{hasFilters ? 'Try adjusting your filters' : `No ${tab === 'all' ? '' : tab} tickets at the moment`}</p>
            </div>
            {hasFilters && <button onClick={clearFilters} className="text-sm text-blue-600 hover:underline">Clear filters</button>}
          </div>
        ) : (
          tickets.map(ticket => (
            <TicketRow
              key={ticket.id}
              ticket={ticket}
              selected={selected.has(ticket.id)}
              onSelect={toggleSelect}
              onAssignToMe={(t) => { assignMutation.mutate(t); }}
              onResolve={(t) => { resolveMutation.mutate(t); }}
            />
          ))
        )}
      </div>
    </div>
  );
}
