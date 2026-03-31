import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { useAuthStore } from '../store/authStore.js';
import { fetchDashboardMetrics, fetchTickets } from '../api/ticketApi.js';

function Icon({ d, className = 'h-5 w-5' }: { d: string; className?: string }) {
  return (
    <svg className={className} fill="none" viewBox="0 0 24 24" strokeWidth={1.5} stroke="currentColor">
      <path strokeLinecap="round" strokeLinejoin="round" d={d} />
    </svg>
  );
}

const PRIORITY_COLOR: Record<string, string> = {
  URGENT: 'bg-red-100 text-red-700 border-red-200',
  HIGH: 'bg-orange-100 text-orange-700 border-orange-200',
  MEDIUM: 'bg-yellow-100 text-yellow-700 border-yellow-200',
  LOW: 'bg-gray-100 text-gray-600 border-gray-200',
};

const STATUS_COLOR: Record<string, string> = {
  OPEN: 'bg-blue-100 text-blue-700',
  IN_PROGRESS: 'bg-indigo-100 text-indigo-700',
  ESCALATED: 'bg-red-100 text-red-700',
  PENDING_CUSTOMER_RESPONSE: 'bg-yellow-100 text-yellow-700',
  RESOLVED: 'bg-green-100 text-green-700',
  CLOSED: 'bg-gray-100 text-gray-600',
};

function MetricCard({ title, value, sub, icon, color, trend }: {
  title: string; value: string | number; sub?: string; icon: string;
  color: string; trend?: { value: string; up: boolean };
}) {
  return (
    <div className="rounded-xl border border-gray-200 bg-white p-5 shadow-sm">
      <div className="flex items-start justify-between">
        <div>
          <p className="text-sm text-gray-500">{title}</p>
          <p className={`mt-1 text-3xl font-bold ${color}`}>{value}</p>
          {sub && <p className="mt-1 text-xs text-gray-400">{sub}</p>}
        </div>
        <div className={`rounded-lg p-2 ${color.replace('text-', 'bg-').replace('-600', '-100').replace('-700', '-100').replace('-500', '-100')}`}>
          <Icon d={icon} className={`h-5 w-5 ${color}`} />
        </div>
      </div>
      {trend && (
        <div className={`mt-3 flex items-center gap-1 text-xs ${trend.up ? 'text-green-600' : 'text-red-500'}`}>
          <Icon d={trend.up ? 'M4.5 10.5L12 3m0 0l7.5 7.5M12 3v18' : 'M19.5 13.5L12 21m0 0l-7.5-7.5M12 21V3'} className="h-3 w-3" />
          {trend.value}
        </div>
      )}
    </div>
  );
}

function timeAgo(iso: string): string {
  const diff = Date.now() - new Date(iso).getTime();
  const m = Math.floor(diff / 60000);
  if (m < 1) return 'just now';
  if (m < 60) return `${m}m ago`;
  const h = Math.floor(m / 60);
  if (h < 24) return `${h}h ago`;
  return `${Math.floor(h / 24)}d ago`;
}

export function DashboardPage() {
  const { token, user } = useAuthStore();
  const safeToken = token ?? '';

  const { data: metrics, isLoading: metricsLoading } = useQuery({
    queryKey: ['dashboard-metrics'],
    queryFn: () => fetchDashboardMetrics(safeToken),
    enabled: !!token,
    staleTime: 60_000,
    refetchInterval: 120_000,
  });

  const { data: urgentData } = useQuery({
    queryKey: ['tickets', 'urgent-mine'],
    queryFn: () => fetchTickets(safeToken, { tab: 'mine', agentId: user?.id }),
    enabled: !!token,
    staleTime: 30_000,
  });

  const { data: unassignedData } = useQuery({
    queryKey: ['tickets', 'unassigned-preview'],
    queryFn: () => fetchTickets(safeToken, { tab: 'unassigned', limit: 5 }),
    enabled: !!token,
    staleTime: 30_000,
  });

  const myTickets = (urgentData?.data ?? []).filter(t => !['RESOLVED', 'CLOSED'].includes(t.status));
  const unassigned = unassignedData?.data ?? [];

  const hour = new Date().getHours();
  const greeting = hour < 12 ? 'Good morning' : hour < 17 ? 'Good afternoon' : 'Good evening';

  return (
    <div className="space-y-6 p-6">
      {/* Header */}
      <div>
        <h1 className="text-2xl font-bold text-gray-900">{greeting}, {user?.name?.split(' ')[0] ?? 'Agent'} 👋</h1>
        <p className="mt-1 text-sm text-gray-500">Here's what's happening with your tickets today.</p>
      </div>

      {/* Metric cards */}
      {metricsLoading ? (
        <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
          {Array.from({ length: 8 }).map((_, i) => (
            <div key={i} className="h-28 animate-pulse rounded-xl bg-gray-100" />
          ))}
        </div>
      ) : (
        <>
          <div>
            <h2 className="mb-3 text-xs font-semibold uppercase tracking-wide text-gray-400">My Performance</h2>
            <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
              <MetricCard title="My Open Tickets" value={metrics?.myOpenTickets ?? 0} icon="M16.5 6v.75m0 3v.75m0 3v.75m0 3V18m-9-5.25h5.25M7.5 15h3M3.375 5.25c-.621 0-1.125.504-1.125 1.125v3.026a2.999 2.999 0 010 5.198v3.026c0 .621.504 1.125 1.125 1.125h17.25c.621 0 1.125-.504 1.125-1.125v-3.026a3 3 0 010-5.198V6.375c0-.621-.504-1.125-1.125-1.125H3.375z" color="text-blue-600" sub="Active tickets assigned to you" />
              <MetricCard title="Urgent Tickets" value={metrics?.myUrgentTickets ?? 0} icon="M12 9v3.75m-9.303 3.376c-.866 1.5.217 3.374 1.948 3.374h14.71c1.73 0 2.813-1.874 1.948-3.374L13.949 3.378c-.866-1.5-3.032-1.5-3.898 0L2.697 16.126zM12 15.75h.007v.008H12v-.008z" color="text-red-600" sub="Need immediate attention" />
              <MetricCard title="Resolved Today" value={metrics?.resolvedToday ?? 0} icon="M9 12.75L11.25 15 15 9.75M21 12a9 9 0 11-18 0 9 9 0 0118 0z" color="text-green-600" trend={{ value: 'Great progress!', up: true }} />
              <MetricCard title="Avg. First Response" value={`${metrics?.avgFirstResponseMinutes ?? 0}m`} icon="M12 6v6h4.5m4.5 0a9 9 0 11-18 0 9 9 0 0118 0z" color="text-purple-600" sub="Minutes to first reply" />
            </div>
          </div>

          <div>
            <h2 className="mb-3 text-xs font-semibold uppercase tracking-wide text-gray-400">Team Overview</h2>
            <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
              <MetricCard title="SLA Breached" value={metrics?.slaBreachedTickets ?? 0} icon="M12 6v6h4.5m4.5 0a9 9 0 11-18 0 9 9 0 0118 0z" color="text-red-600" sub="Require escalation" />
              <MetricCard title="Unassigned" value={metrics?.unassignedTickets ?? 0} icon="M15.75 6a3.75 3.75 0 11-7.5 0 3.75 3.75 0 017.5 0zM4.501 20.118a7.5 7.5 0 0114.998 0A17.933 17.933 0 0112 21.75c-2.676 0-5.216-.584-7.499-1.632z" color="text-orange-600" sub="Awaiting assignment" />
              <MetricCard title="Team Open" value={metrics?.teamOpenTickets ?? 0} icon="M18 18.72a9.094 9.094 0 003.741-.479 3 3 0 00-4.682-2.72m.94 3.198l.001.031c0 .225-.012.447-.037.666A11.944 11.944 0 0112 21c-2.17 0-4.207-.576-5.963-1.584A6.062 6.062 0 016 18.719m12 0a5.971 5.971 0 00-.941-3.197m0 0A5.995 5.995 0 0012 12.75a5.995 5.995 0 00-5.058 2.772m0 0a3 3 0 00-4.681 2.72 8.986 8.986 0 003.74.477m.94-3.197a5.971 5.971 0 00-.94 3.197M15 6.75a3 3 0 11-6 0 3 3 0 016 0zm6 3a2.25 2.25 0 11-4.5 0 2.25 2.25 0 014.5 0zm-13.5 0a2.25 2.25 0 11-4.5 0 2.25 2.25 0 014.5 0z" color="text-indigo-600" sub="Total across all agents" />
              <MetricCard title="CSAT Score" value={`${metrics?.satisfactionScore?.toFixed(1) ?? '—'}/5`} icon="M11.48 3.499a.562.562 0 011.04 0l2.125 5.111a.563.563 0 00.475.345l5.518.442c.499.04.701.663.321.988l-4.204 3.602a.563.563 0 00-.182.557l1.285 5.385a.562.562 0 01-.84.61l-4.725-2.885a.563.563 0 00-.586 0L6.982 20.54a.562.562 0 01-.84-.61l1.285-5.386a.562.562 0 00-.182-.557l-4.204-3.602a.563.563 0 01.321-.988l5.518-.442a.563.563 0 00.475-.345L11.48 3.5z" color="text-yellow-600" sub="Customer satisfaction" />
            </div>
          </div>
        </>
      )}

      {/* Two column: My Tickets + Unassigned */}
      <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
        {/* My Active Tickets */}
        <div className="rounded-xl border border-gray-200 bg-white shadow-sm">
          <div className="flex items-center justify-between border-b border-gray-100 px-5 py-4">
            <h3 className="font-semibold text-gray-900">My Active Tickets</h3>
            <Link to="/tickets?tab=mine" className="text-xs text-blue-600 hover:underline">View all →</Link>
          </div>
          <div className="divide-y divide-gray-50">
            {myTickets.length === 0 ? (
              <div className="py-10 text-center">
                <p className="text-sm font-medium text-gray-500">All clear! No active tickets.</p>
                <p className="mt-1 text-xs text-gray-400">Check the queue for unassigned tickets.</p>
              </div>
            ) : (
              myTickets.slice(0, 6).map(ticket => (
                <Link key={ticket.id} to={`/tickets/${ticket.ticketNumber}`} className="flex items-start gap-3 px-5 py-3 transition-colors hover:bg-gray-50">
                  <div className="min-w-0 flex-1">
                    <p className="truncate text-sm font-medium text-gray-900">{ticket.title}</p>
                    <div className="mt-1 flex items-center gap-2">
                      <span className="text-xs text-gray-400">{ticket.ticketNumber}</span>
                      <span className="text-gray-300">•</span>
                      <span className="text-xs text-gray-500">{ticket.customerName}</span>
                    </div>
                  </div>
                  <div className="flex shrink-0 flex-col items-end gap-1">
                    <span className={`rounded-full border px-2 py-0.5 text-[10px] font-medium ${PRIORITY_COLOR[ticket.priority] ?? 'bg-gray-100 text-gray-600'}`}>{ticket.priority}</span>
                    {ticket.slaBreached && <span className="rounded-full bg-red-100 px-2 py-0.5 text-[10px] font-medium text-red-700">SLA!</span>}
                    <span className="text-[10px] text-gray-400">{timeAgo(ticket.updatedAt)}</span>
                  </div>
                </Link>
              ))
            )}
          </div>
        </div>

        {/* Unassigned Tickets */}
        <div className="rounded-xl border border-gray-200 bg-white shadow-sm">
          <div className="flex items-center justify-between border-b border-gray-100 px-5 py-4">
            <h3 className="font-semibold text-gray-900">Unassigned Tickets</h3>
            <Link to="/tickets?tab=unassigned" className="text-xs text-blue-600 hover:underline">View all →</Link>
          </div>
          <div className="divide-y divide-gray-50">
            {unassigned.length === 0 ? (
              <div className="py-10 text-center">
                <p className="text-sm font-medium text-gray-500">No unassigned tickets.</p>
                <p className="mt-1 text-xs text-gray-400">Great teamwork!</p>
              </div>
            ) : (
              unassigned.slice(0, 6).map(ticket => (
                <Link key={ticket.id} to={`/tickets/${ticket.ticketNumber}`} className="flex items-start gap-3 px-5 py-3 transition-colors hover:bg-gray-50">
                  <div className="min-w-0 flex-1">
                    <p className="truncate text-sm font-medium text-gray-900">{ticket.title}</p>
                    <div className="mt-1 flex items-center gap-2">
                      <span className="text-xs text-gray-400">{ticket.ticketNumber}</span>
                      <span className="text-gray-300">•</span>
                      <span className="text-xs text-gray-500">{ticket.categoryName}</span>
                    </div>
                  </div>
                  <div className="flex shrink-0 flex-col items-end gap-1">
                    <span className={`rounded-md px-2 py-0.5 text-[10px] font-medium ${STATUS_COLOR[ticket.status] ?? 'bg-gray-100 text-gray-600'}`}>{ticket.status.replace(/_/g, ' ')}</span>
                    <span className="text-[10px] text-gray-400">{timeAgo(ticket.createdAt)}</span>
                  </div>
                </Link>
              ))
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
