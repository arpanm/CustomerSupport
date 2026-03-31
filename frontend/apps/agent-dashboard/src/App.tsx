import { useState, useRef, useEffect } from 'react';
import { createBrowserRouter, Navigate, Outlet, NavLink, useNavigate } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useAuthStore } from './store/authStore.js';
import { useAgentStore } from './store/agentStore.js';
import { useWebSocketStore } from './store/websocketStore.js';
import { LoginPage } from './pages/LoginPage.js';
import { TicketQueuePage } from './pages/TicketQueuePage.js';
import { TicketDetailPage } from './pages/TicketDetailPage.js';
import { DashboardPage } from './pages/DashboardPage.js';
import { fetchNotifications, markNotificationRead } from './api/ticketApi.js';
import type { Notification } from './api/ticketApi.js';
import type { AgentStatus } from './store/agentStore.js';

function Icon({ d, className = 'h-5 w-5' }: { d: string; className?: string }) {
  return (
    <svg className={className} fill="none" viewBox="0 0 24 24" strokeWidth={1.5} stroke="currentColor">
      <path strokeLinecap="round" strokeLinejoin="round" d={d} />
    </svg>
  );
}

const ICONS = {
  dashboard: 'M2.25 12l8.954-8.955c.44-.439 1.152-.439 1.591 0L21.75 12M4.5 9.75v10.125c0 .621.504 1.125 1.125 1.125H9.75v-4.875c0-.621.504-1.125 1.125-1.125h2.25c.621 0 1.125.504 1.125 1.125V21h4.125c.621 0 1.125-.504 1.125-1.125V9.75M8.25 21h8.25',
  tickets: 'M16.5 6v.75m0 3v.75m0 3v.75m0 3V18m-9-5.25h5.25M7.5 15h3M3.375 5.25c-.621 0-1.125.504-1.125 1.125v3.026a2.999 2.999 0 010 5.198v3.026c0 .621.504 1.125 1.125 1.125h17.25c.621 0 1.125-.504 1.125-1.125v-3.026a3 3 0 010-5.198V6.375c0-.621-.504-1.125-1.125-1.125H3.375z',
  bell: 'M14.857 17.082a23.848 23.848 0 005.454-1.31A8.967 8.967 0 0118 9.75v-.7V9A6 6 0 006 9v.75a8.967 8.967 0 01-2.312 6.022c1.733.64 3.56 1.085 5.455 1.31m5.714 0a24.255 24.255 0 01-5.714 0m5.714 0a3 3 0 11-5.714 0',
  logout: 'M15.75 9V5.25A2.25 2.25 0 0013.5 3h-6a2.25 2.25 0 00-2.25 2.25v13.5A2.25 2.25 0 007.5 21h6a2.25 2.25 0 002.25-2.25V15m3 0l3-3m0 0l-3-3m3 3H9',
  chevronDown: 'M19.5 8.25l-7.5 7.5-7.5-7.5',
  clock: 'M12 6v6h4.5m4.5 0a9 9 0 11-18 0 9 9 0 0118 0z',
  exclamation: 'M12 9v3.75m-9.303 3.376c-.866 1.5.217 3.374 1.948 3.374h14.71c1.73 0 2.813-1.874 1.948-3.374L13.949 3.378c-.866-1.5-3.032-1.5-3.898 0L2.697 16.126zM12 15.75h.007v.008H12v-.008z',
  check: 'M4.5 12.75l6 6 9-13.5',
};

const STATUS_META: Record<AgentStatus, { label: string; dot: string; bg: string }> = {
  AVAILABLE: { label: 'Available', dot: 'bg-green-500', bg: 'bg-green-50 text-green-800' },
  BUSY: { label: 'Busy', dot: 'bg-yellow-400', bg: 'bg-yellow-50 text-yellow-800' },
  OFFLINE: { label: 'Offline', dot: 'bg-gray-400', bg: 'bg-gray-100 text-gray-600' },
};
const STATUS_CYCLE: AgentStatus[] = ['AVAILABLE', 'BUSY', 'OFFLINE'];

function timeAgo(iso: string): string {
  const diff = Date.now() - new Date(iso).getTime();
  const m = Math.floor(diff / 60000);
  if (m < 1) return 'just now';
  if (m < 60) return `${m}m ago`;
  const h = Math.floor(m / 60);
  if (h < 24) return `${h}h ago`;
  return `${Math.floor(h / 24)}d ago`;
}

function NotificationBell({ token }: { token: string }) {
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);
  const queryClient = useQueryClient();

  const { data: notifications = [] } = useQuery({
    queryKey: ['notifications'],
    queryFn: () => fetchNotifications(token),
    refetchInterval: 30_000,
    staleTime: 15_000,
  });

  const markReadMutation = useMutation({
    mutationFn: (id: string) => markNotificationRead(token, id),
    onMutate: async (id) => {
      await queryClient.cancelQueries({ queryKey: ['notifications'] });
      const prev = queryClient.getQueryData<Notification[]>(['notifications']);
      queryClient.setQueryData<Notification[]>(['notifications'], old =>
        (old ?? []).map(n => n.id === id ? { ...n, isRead: true } : n),
      );
      return { prev };
    },
    onError: (_e, _id, ctx) => { if (ctx?.prev) queryClient.setQueryData(['notifications'], ctx.prev); },
  });

  const unread = (notifications as Notification[]).filter(n => !n.isRead).length;

  useEffect(() => {
    const handler = (e: MouseEvent) => { if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false); };
    document.addEventListener('mousedown', handler);
    return () => { document.removeEventListener('mousedown', handler); };
  }, []);

  const TYPE_STYLE: Record<string, string> = {
    SLA_BREACH: 'text-red-500', SLA_WARNING: 'text-orange-500',
    TICKET_ASSIGNED: 'text-blue-500', CUSTOMER_REPLY: 'text-green-500', TICKET_ESCALATED: 'text-purple-500',
  };

  return (
    <div className="relative" ref={ref}>
      <button onClick={() => { setOpen(v => !v); }} className="relative rounded-lg p-2 text-gray-500 hover:bg-gray-100 hover:text-gray-700" aria-label="Notifications">
        <Icon d={ICONS.bell} />
        {unread > 0 && (
          <span className="absolute right-1 top-1 flex h-4 w-4 items-center justify-center rounded-full bg-red-500 text-[10px] font-bold text-white">{unread > 9 ? '9+' : unread}</span>
        )}
      </button>

      {open && (
        <div className="absolute right-0 top-full z-50 mt-2 w-96 rounded-xl border border-gray-200 bg-white shadow-xl">
          <div className="flex items-center justify-between border-b border-gray-100 px-4 py-3">
            <h3 className="font-semibold text-gray-900">Notifications</h3>
            {unread > 0 && <span className="rounded-full bg-red-100 px-2 py-0.5 text-xs text-red-600">{unread} unread</span>}
          </div>
          <div className="max-h-96 overflow-y-auto divide-y divide-gray-50">
            {(notifications as Notification[]).length === 0 ? (
              <div className="py-10 text-center text-sm text-gray-400">All caught up!</div>
            ) : (
              (notifications as Notification[]).slice(0, 10).map(n => (
                <div key={n.id} className={`flex gap-3 px-4 py-3 transition-colors hover:bg-gray-50 ${n.isRead ? 'opacity-60' : 'bg-blue-50/30'}`}>
                  <div className={`mt-0.5 shrink-0 ${TYPE_STYLE[n.type] ?? 'text-gray-400'}`}>
                    <Icon d={n.type.includes('SLA') ? ICONS.clock : ICONS.exclamation} className="h-4 w-4" />
                  </div>
                  <div className="min-w-0 flex-1">
                    <p className="text-sm font-medium text-gray-900">{n.title}</p>
                    <p className="mt-0.5 line-clamp-2 text-xs text-gray-500">{n.message}</p>
                    <p className="mt-1 text-xs text-gray-400">{timeAgo(n.createdAt)}</p>
                  </div>
                  {!n.isRead && (
                    <button onClick={(e) => { e.stopPropagation(); markReadMutation.mutate(n.id); }} className="shrink-0 self-start rounded-full p-1 text-gray-400 hover:bg-white hover:text-gray-600" title="Mark read">
                      <Icon d={ICONS.check} className="h-3.5 w-3.5" />
                    </button>
                  )}
                </div>
              ))
            )}
          </div>
        </div>
      )}
    </div>
  );
}

function ProtectedLayout() {
  const { isAuthenticated, user, token, logout } = useAuthStore();
  const { agentStatus, setAgentStatus } = useAgentStore();
  const { connect, updateCount, resetUpdateCount } = useWebSocketStore();
  const navigate = useNavigate();
  const [statusOpen, setStatusOpen] = useState(false);
  const statusRef = useRef<HTMLDivElement>(null);

  useEffect(() => { if (token && user?.tenantId) connect(user.tenantId, token); }, [token, user?.tenantId, connect]);

  useEffect(() => {
    const handler = (e: MouseEvent) => { if (statusRef.current && !statusRef.current.contains(e.target as Node)) setStatusOpen(false); };
    document.addEventListener('mousedown', handler);
    return () => { document.removeEventListener('mousedown', handler); };
  }, []);

  if (!isAuthenticated) return <Navigate to="/login" replace />;

  const meta = STATUS_META[agentStatus];

  return (
    <div className="flex h-screen overflow-hidden bg-gray-50">
      {/* ── Sidebar ── */}
      <aside className="flex w-56 shrink-0 flex-col border-r border-gray-200 bg-white">
        <div className="flex items-center gap-2.5 border-b border-gray-100 px-4 py-[14px]">
          <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-blue-600 text-white text-sm font-bold">S</div>
          <div>
            <p className="text-sm font-bold text-gray-900">SupportHub</p>
            <p className="text-[10px] text-gray-400 leading-none">Agent Dashboard</p>
          </div>
        </div>

        <nav className="flex-1 space-y-0.5 px-2 py-3">
          {[{ to: '/', icon: ICONS.dashboard, label: 'Dashboard', end: true }, { to: '/tickets', icon: ICONS.tickets, label: 'Ticket Queue', end: false }].map(item => (
            <NavLink key={item.to} to={item.to} end={item.end}
              className={({ isActive }) => `flex items-center gap-3 rounded-lg px-3 py-2.5 text-sm font-medium transition-colors ${isActive ? 'bg-blue-50 text-blue-700' : 'text-gray-600 hover:bg-gray-100 hover:text-gray-900'}`}
            >
              <Icon d={item.icon} className="h-4 w-4 shrink-0" />
              {item.label}
            </NavLink>
          ))}
        </nav>

        <div className="space-y-1.5 border-t border-gray-100 p-3">
          <div className="relative" ref={statusRef}>
            <button onClick={() => { setStatusOpen(v => !v); }} className={`flex w-full items-center justify-between rounded-lg px-3 py-2 text-xs font-medium transition-colors ${meta.bg}`}>
              <div className="flex items-center gap-2"><span className={`h-2 w-2 rounded-full ${meta.dot}`} />{meta.label}</div>
              <Icon d={ICONS.chevronDown} className="h-3 w-3" />
            </button>
            {statusOpen && (
              <div className="absolute bottom-full left-0 mb-1 w-full rounded-lg border border-gray-200 bg-white shadow-lg z-10">
                {STATUS_CYCLE.map(s => (
                  <button key={s} onClick={() => { if (token) setAgentStatus(s, token); setStatusOpen(false); }} className="flex w-full items-center gap-2 px-3 py-2 text-xs hover:bg-gray-50">
                    <span className={`h-2 w-2 rounded-full ${STATUS_META[s].dot}`} />{STATUS_META[s].label}
                  </button>
                ))}
              </div>
            )}
          </div>

          <div className="flex items-center gap-2 px-2 py-1">
            <div className="flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-blue-600 text-xs font-bold text-white">
              {(user?.name ?? 'A').charAt(0).toUpperCase()}
            </div>
            <div className="min-w-0 flex-1">
              <p className="truncate text-xs font-semibold text-gray-900">{user?.name ?? 'Agent'}</p>
              <p className="truncate text-[10px] text-gray-400">{user?.role}</p>
            </div>
          </div>

          <button onClick={() => { logout(); navigate('/login'); }} className="flex w-full items-center gap-2 rounded-lg px-3 py-1.5 text-xs text-red-500 hover:bg-red-50">
            <Icon d={ICONS.logout} className="h-3.5 w-3.5" />Sign out
          </button>
        </div>
      </aside>

      {/* ── Main ── */}
      <div className="flex flex-1 flex-col overflow-hidden">
        <header className="flex h-14 shrink-0 items-center justify-between border-b border-gray-200 bg-white px-6">
          <div>
            {updateCount > 0 && (
              <button onClick={resetUpdateCount} className="flex items-center gap-1.5 rounded-full bg-blue-600 px-2.5 py-1 text-xs font-medium text-white hover:bg-blue-700">
                <span className="h-1.5 w-1.5 animate-pulse rounded-full bg-white" />
                {updateCount} live update{updateCount !== 1 ? 's' : ''}
              </button>
            )}
          </div>
          <div className="flex items-center gap-2">
            {token && <NotificationBell token={token} />}
          </div>
        </header>
        <main className="flex-1 overflow-auto">
          <Outlet />
        </main>
      </div>
    </div>
  );
}

export const router = createBrowserRouter([
  { path: '/login', element: <LoginPage /> },
  {
    path: '/',
    element: <ProtectedLayout />,
    children: [
      { index: true, element: <DashboardPage /> },
      { path: 'tickets', element: <TicketQueuePage /> },
      { path: 'tickets/:ticketNumber', element: <TicketDetailPage /> },
    ],
  },
]);
