import { createBrowserRouter, Navigate, Outlet } from 'react-router-dom';
import { useAuthStore } from './store/authStore.js';
import { LoginPage } from './pages/LoginPage.js';
import { TicketQueuePage } from './pages/TicketQueuePage.js';

function ProtectedLayout() {
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  return (
    <div className="flex min-h-screen bg-gray-50">
      <aside className="w-64 border-r border-gray-200 bg-white shadow-sm">
        <div className="p-4">
          <h1 className="text-lg font-bold text-blue-600">SupportHub</h1>
          <p className="text-xs text-gray-500">Agent Dashboard</p>
        </div>
        <nav className="mt-4 px-2">
          <a
            href="/"
            className="flex items-center rounded-md px-3 py-2 text-sm font-medium text-gray-700 hover:bg-gray-100"
          >
            Ticket Queue
          </a>
        </nav>
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
    ],
  },
]);
