import { useEffect } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { Card, CardContent, CardHeader, CardTitle, TicketStatusBadge, Spinner, Badge } from '@supporthub/ui';
import { useAuthStore } from '../store/authStore.js';
import { useTicketFilterStore } from '../store/ticketStore.js';
import { useWebSocketStore } from '../store/websocketStore.js';
import { useAgentStore } from '../store/agentStore.js';
import { fetchTickets } from '../api/ticketApi.js';
import type { TicketSummary } from '../api/ticketApi.js';
import type { AgentStatus } from '../store/agentStore.js';

const PRIORITY_VARIANT: Record<string, 'default' | 'destructive' | 'secondary' | 'outline'> = {
  URGENT: 'destructive',
  HIGH: 'destructive',
  MEDIUM: 'default',
  LOW: 'secondary',
};

const SENTIMENT_EMOJI: Record<string, string> = {
  very_negative: '😡',
  negative: '😞',
  neutral: '😐',
  positive: '😊',
  very_positive: '😄',
};

const AGENT_STATUS_LABEL: Record<AgentStatus, string> = {
  AVAILABLE: 'Available',
  BUSY: 'Busy',
  OFFLINE: 'Offline',
};

const AGENT_STATUS_CLASS: Record<AgentStatus, string> = {
  AVAILABLE: 'bg-green-100 text-green-800 hover:bg-green-200',
  BUSY: 'bg-yellow-100 text-yellow-800 hover:bg-yellow-200',
  OFFLINE: 'bg-gray-100 text-gray-800 hover:bg-gray-200',
};

const AGENT_STATUS_DOT: Record<AgentStatus, string> = {
  AVAILABLE: 'bg-green-500',
  BUSY: 'bg-yellow-500',
  OFFLINE: 'bg-gray-400',
};

const STATUS_CYCLE: AgentStatus[] = ['AVAILABLE', 'BUSY', 'OFFLINE'];

function TicketFilters() {
  const { filter, setFilter, resetFilter } = useTicketFilterStore();

  return (
    <div className="flex flex-wrap items-center gap-3 rounded-lg border border-gray-200 bg-white p-3">
      <input
        type="text"
        placeholder="Search tickets..."
        value={filter.search}
        onChange={(e) => { setFilter({ search: e.target.value }); }}
        className="w-48 rounded-md border border-gray-300 px-3 py-1.5 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
      />
      <select
        value={filter.status}
        onChange={(e) => { setFilter({ status: e.target.value as typeof filter.status }); }}
        className="rounded-md border border-gray-300 px-3 py-1.5 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
      >
        <option value="">All Statuses</option>
        <option value="OPEN">Open</option>
        <option value="IN_PROGRESS">In Progress</option>
        <option value="PENDING_AGENT_RESPONSE">Pending Response</option>
        <option value="ESCALATED">Escalated</option>
        <option value="RESOLVED">Resolved</option>
      </select>
      <select
        value={filter.priority}
        onChange={(e) => { setFilter({ priority: e.target.value as typeof filter.priority }); }}
        className="rounded-md border border-gray-300 px-3 py-1.5 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
      >
        <option value="">All Priorities</option>
        <option value="URGENT">Urgent</option>
        <option value="HIGH">High</option>
        <option value="MEDIUM">Medium</option>
        <option value="LOW">Low</option>
      </select>
      <label className="flex items-center gap-2 text-sm text-gray-700">
        <input
          type="checkbox"
          checked={filter.assignedToMe}
          onChange={(e) => { setFilter({ assignedToMe: e.target.checked }); }}
          className="rounded border-gray-300"
        />
        Assigned to me
      </label>
      <button onClick={resetFilter} className="text-sm text-blue-600 hover:underline">
        Clear filters
      </button>
    </div>
  );
}

function TicketCard({ ticket }: { ticket: TicketSummary }) {
  return (
    <Link to={`/tickets/${ticket.ticketNumber}`}>
      <Card className="cursor-pointer transition-all hover:border-blue-200 hover:shadow-md">
        <CardHeader className="pb-2">
          <div className="flex items-start justify-between gap-4">
            <div className="min-w-0 flex-1">
              <CardTitle className="truncate text-base">{ticket.title}</CardTitle>
              <p className="mt-0.5 text-xs text-gray-500">
                {ticket.ticketNumber}
                {ticket.categoryName && (
                  <span className="ml-2 text-gray-400">• {ticket.categoryName}</span>
                )}
              </p>
            </div>
            <div className="flex shrink-0 items-center gap-2">
              {ticket.sentimentLabel != null && (
                <span title={ticket.sentimentLabel} className="text-base">
                  {SENTIMENT_EMOJI[ticket.sentimentLabel] ?? ''}
                </span>
              )}
              {ticket.slaBreached && (
                <span className="rounded-full bg-red-100 px-2 py-0.5 text-xs font-medium text-red-700">
                  SLA Breach
                </span>
              )}
              <Badge variant={PRIORITY_VARIANT[ticket.priority] ?? 'secondary'}>
                {ticket.priority}
              </Badge>
              <TicketStatusBadge status={ticket.status} />
            </div>
          </div>
        </CardHeader>
        <CardContent className="pt-0">
          <div className="flex items-center justify-between text-xs text-gray-500">
            <span>
              {ticket.customerName}
              {ticket.assignedAgentId != null && (
                <span className="ml-2 text-blue-500">• Assigned</span>
              )}
            </span>
            <span>{new Date(ticket.createdAt).toLocaleDateString('en-IN')}</span>
          </div>
        </CardContent>
      </Card>
    </Link>
  );
}

export function TicketQueuePage() {
  const { token, user } = useAuthStore();
  const { filter } = useTicketFilterStore();
  const { lastTicketUpdate, updateCount, resetUpdateCount, connect } = useWebSocketStore();
  const { agentStatus, setAgentStatus } = useAgentStore();
  const queryClient = useQueryClient();

  useEffect(() => {
    if (token != null && user?.tenantId != null) {
      connect(user.tenantId, token);
    }
  }, [token, user?.tenantId, connect]);

  // Auto-refresh ticket list on STOMP events
  useEffect(() => {
    if (lastTicketUpdate != null) {
      void queryClient.invalidateQueries({ queryKey: ['tickets', 'queue'] });
    }
  }, [lastTicketUpdate, queryClient]);

  const { data, isLoading, isError, error } = useQuery({
    queryKey: ['tickets', 'queue', filter],
    queryFn: () =>
      fetchTickets(token ?? '', {
        status: filter.status || undefined,
        priority: filter.priority || undefined,
        assignedToMe: filter.assignedToMe,
        search: filter.search || undefined,
        agentId: user?.id,
      }),
    enabled: token != null,
    staleTime: 30_000,
    refetchInterval: 60_000,
  });

  const handleCycleStatus = () => {
    if (token == null) return;
    const idx = STATUS_CYCLE.indexOf(agentStatus);
    const next = STATUS_CYCLE[(idx + 1) % STATUS_CYCLE.length];
    setAgentStatus(next, token);
  };

  if (isLoading) {
    return (
      <div className="flex h-64 items-center justify-center">
        <Spinner size="lg" label="Loading ticket queue..." />
      </div>
    );
  }

  if (isError) {
    return (
      <div className="rounded-md border border-red-200 bg-red-50 px-4 py-6 text-center text-red-700">
        <p className="font-medium">Failed to load tickets</p>
        <p className="mt-1 text-sm">{error instanceof Error ? error.message : 'Unknown error'}</p>
      </div>
    );
  }

  const tickets = data?.data ?? [];
  const total = data?.pagination.total ?? tickets.length;

  return (
    <div className="flex flex-col gap-4">
      {/* Header row */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-3">
          <h2 className="text-2xl font-bold text-gray-900">Ticket Queue</h2>

          {/* Live update count badge */}
          {updateCount > 0 && (
            <button
              onClick={resetUpdateCount}
              className="flex items-center gap-1.5 rounded-full bg-blue-600 px-2.5 py-0.5 text-xs font-medium text-white hover:bg-blue-700"
              title="Live updates received — click to dismiss"
            >
              <span className="h-1.5 w-1.5 rounded-full bg-white" />
              {updateCount} live update{updateCount !== 1 ? 's' : ''}
            </button>
          )}
        </div>

        <div className="flex items-center gap-3">
          {/* Agent status toggle */}
          <button
            onClick={handleCycleStatus}
            className={`flex items-center gap-1.5 rounded-full px-3 py-1 text-xs font-medium transition-colors ${AGENT_STATUS_CLASS[agentStatus]}`}
            title="Click to change status"
          >
            <span className={`h-2 w-2 rounded-full ${AGENT_STATUS_DOT[agentStatus]}`} />
            {AGENT_STATUS_LABEL[agentStatus]}
          </button>

          <span className="text-sm text-gray-500">
            {total} ticket{total !== 1 ? 's' : ''}
          </span>
        </div>
      </div>

      <TicketFilters />

      {tickets.length === 0 ? (
        <Card>
          <CardContent className="py-12 text-center text-gray-500">
            <p className="text-lg font-medium">No tickets found</p>
            <p className="mt-1 text-sm">Try adjusting your filters</p>
          </CardContent>
        </Card>
      ) : (
        <div className="flex flex-col gap-3">
          {tickets.map((ticket) => (
            <TicketCard key={ticket.id} ticket={ticket} />
          ))}
          {data?.pagination.hasMore === true && (
            <button className="rounded-md border border-gray-300 bg-white px-4 py-2 text-sm text-gray-700 hover:bg-gray-50">
              Load more
            </button>
          )}
        </div>
      )}
    </div>
  );
}
