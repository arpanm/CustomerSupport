import { createBrowserRouter, Outlet, NavLink, Navigate } from 'react-router-dom';
import { useAuthStore } from './store/authStore.js';
import { DashboardPage } from './pages/DashboardPage.js';
import { CategoryManagementPage } from './pages/CategoryManagementPage.js';
import { AgentManagementPage } from './pages/AgentManagementPage.js';
import { SlaConfigPage } from './pages/SlaConfigPage.js';
import { ReportingPage } from './pages/ReportingPage.js';
import { FAQManagementPage } from './pages/FAQManagementPage.js';
import { LoginPage } from './pages/LoginPage.js';

const NAV_LINK_CLASS = ({ isActive }: { isActive: boolean }) =>
  `flex items-center rounded-md px-3 py-2 text-sm font-medium transition-colors ${
    isActive ? 'bg-blue-50 text-blue-700' : 'text-gray-700 hover:bg-gray-100'
  }`;

function AdminLayout() {
  const { isAuthenticated, user, logout } = useAuthStore();

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  return (
    <div className="flex min-h-screen bg-gray-50">
      <aside className="flex w-64 flex-col border-r border-gray-200 bg-white shadow-sm">
        <div className="p-4">
          <h1 className="text-lg font-bold text-blue-600">SupportHub</h1>
          <p className="text-xs text-gray-500">Admin Portal</p>
        </div>
        <nav className="mt-4 flex-1 space-y-1 px-2">
          <NavLink to="/" end className={NAV_LINK_CLASS}>Dashboard</NavLink>
          <NavLink to="/agents" className={NAV_LINK_CLASS}>Agents</NavLink>
          <NavLink to="/categories" className={NAV_LINK_CLASS}>Categories</NavLink>
          <NavLink to="/sla" className={NAV_LINK_CLASS}>SLA Config</NavLink>
          <NavLink to="/reporting" className={NAV_LINK_CLASS}>Reporting</NavLink>
          <NavLink to="/faqs" className={NAV_LINK_CLASS}>FAQs</NavLink>
        </nav>
        <div className="border-t border-gray-200 p-4">
          <p className="text-xs font-medium text-gray-700">{user?.name ?? 'Admin'}</p>
          <p className="text-xs text-gray-400">{user?.role}</p>
          <button onClick={logout} className="mt-2 text-xs text-red-500 hover:underline">
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
    element: <AdminLayout />,
    children: [
      { index: true, element: <DashboardPage /> },
      { path: 'agents', element: <AgentManagementPage /> },
      { path: 'categories', element: <CategoryManagementPage /> },
      { path: 'sla', element: <SlaConfigPage /> },
      { path: 'reporting', element: <ReportingPage /> },
      { path: 'faqs', element: <FAQManagementPage /> },
    ],
  },
]);
