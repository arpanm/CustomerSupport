import { createBrowserRouter, Navigate, Outlet, NavLink } from 'react-router-dom';
import { useAuthStore } from './store/authStore.js';
import { LoginPage } from './pages/LoginPage.js';
import { TicketQueuePage } from './pages/TicketQueuePage.js';
import { TicketDetailPage } from './pages/TicketDetailPage.js';

const NAV_LINK_CLASS = ({ isActive }: { isActive: boolean }) =>
  `flex items-center rounded-md px-3 py-2 text-sm font-medium transition-colors ${
    isActive
      ? 'bg-blue-50 text-blue-700'
      : 'text-gray-700 hover:bg-gray-100'
  }`;

function ProtectedLayout() {
  const { isAuthenticated, user, logout } = useAuthStore();

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  return (
    <div className="flex min-h-screen bg-gray-50">
      <aside className="flex w-64 flex-col border-r border-gray-200 bg-white shadow-sm">
        <div className="p-4">
          <h1 className="text-lg font-bold text-blue-600">SupportHub</h1>
          <p className="text-xs text-gray-500">Agent Dashboard</p>
        </div>
        <nav className="mt-4 flex-1 space-y-1 px-2">
          <NavLink to="/" end className={NAV_LINK_CLASS}>
            Ticket Queue
          </NavLink>
        </nav>
        <div className="border-t border-gray-200 p-4">
          <p className="text-xs font-medium text-gray-700">{user?.name ?? 'Agent'}</p>
          <p className="text-xs text-gray-400">{user?.role}</p>
          <button
            onClick={logout}
            className="mt-2 text-xs text-red-500 hover:underline"
          >
            Sign out
          </button>
        </div>
      </aside>
      <main className="flex-1 overflow-auto p-8">
        <Outlet />
      </main>
    </div>
  );
}

export const router = createBrowserRouter([
  {
    path: '/login',
    element: <LoginPage />,
  },
  {
    path: '/',
    element: <ProtectedLayout />,
    children: [
      {
        index: true,
        element: <TicketQueuePage />,
      },
      {
        path: 'tickets/:ticketNumber',
        element: <TicketDetailPage />,
      },
    ],
  },
]);
