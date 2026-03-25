import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { useAuthStore } from '../store/authStore.js';
import { getApiClient } from '../api/client.js';

interface NavItem {
  to: string;
  label: string;
}

const NAV_ITEMS: NavItem[] = [
  { to: '/tickets', label: 'My Tickets' },
  { to: '/faq', label: 'FAQs' },
  { to: '/orders', label: 'Orders' },
  { to: '/notifications', label: 'Notifications' },
];

function NavLinkItem({ to, label, badge }: NavItem & { badge?: number }) {
  return (
    <NavLink
      to={to}
      className={({ isActive }) =>
        `relative flex items-center gap-1 rounded-md px-3 py-2 text-sm font-medium transition-colors ${
          isActive
            ? 'bg-blue-50 text-blue-600'
            : 'text-gray-600 hover:bg-gray-100 hover:text-gray-900'
        }`
      }
    >
      {label}
      {badge !== undefined && badge > 0 && (
        <span className="inline-flex h-4 min-w-4 items-center justify-center rounded-full bg-red-500 px-1 text-xs font-bold text-white">
          {badge > 99 ? '99+' : badge}
        </span>
      )}
    </NavLink>
  );
}

export function Layout() {
  const tenantId = useAuthStore((s) => s.tenantId) ?? '';
  const clearAuth = useAuthStore((s) => s.clearAuth);
  const navigate = useNavigate();
  const client = getApiClient(tenantId);

  const { data: unreadCount } = useQuery({
    queryKey: ['unreadCount'],
    queryFn: () => client.getUnreadCount(),
    refetchInterval: 30_000,
  });

  function handleLogout() {
    clearAuth();
    void navigate('/login');
  }

  return (
    <div className="min-h-screen bg-gray-50">
      <header className="sticky top-0 z-10 border-b border-gray-200 bg-white shadow-sm">
        <div className="mx-auto flex max-w-5xl items-center justify-between px-4 py-3">
          <NavLink to="/" className="text-lg font-bold text-blue-600">
            SupportHub
          </NavLink>

          <nav className="hidden items-center gap-1 sm:flex">
            {NAV_ITEMS.map((item) => (
              <NavLinkItem
                key={item.to}
                to={item.to}
                label={item.label}
                badge={item.to === '/notifications' ? (unreadCount ?? 0) : undefined}
              />
            ))}
          </nav>

          <button
            type="button"
            onClick={handleLogout}
            className="rounded-md border border-gray-300 px-3 py-1.5 text-sm font-medium text-gray-700 hover:bg-gray-50 transition-colors"
          >
            Logout
          </button>
        </div>

        <nav className="flex items-center gap-1 overflow-x-auto border-t border-gray-100 px-4 py-2 sm:hidden">
          {NAV_ITEMS.map((item) => (
            <NavLinkItem
              key={item.to}
              to={item.to}
              label={item.label}
              badge={item.to === '/notifications' ? (unreadCount ?? 0) : undefined}
            />
          ))}
        </nav>
      </header>

      <main className="mx-auto max-w-5xl px-4 py-8">
        <Outlet />
      </main>
    </div>
  );
}
