import { createBrowserRouter, Outlet } from 'react-router-dom';
import { DashboardPage } from './pages/DashboardPage.js';

function AdminLayout() {
  return (
    <div className="flex min-h-screen bg-gray-50">
      <aside className="w-64 border-r border-gray-200 bg-white shadow-sm">
        <div className="p-4">
          <h1 className="text-lg font-bold text-blue-600">SupportHub</h1>
          <p className="text-xs text-gray-500">Admin Portal</p>
        </div>
        <nav className="mt-4 px-2">
          <a
            href="/"
            className="flex items-center rounded-md px-3 py-2 text-sm font-medium text-gray-700 hover:bg-gray-100"
          >
            Dashboard
          </a>
          <a
            href="/agents"
            className="flex items-center rounded-md px-3 py-2 text-sm font-medium text-gray-700 hover:bg-gray-100"
          >
            Agents
          </a>
          <a
            href="/categories"
            className="flex items-center rounded-md px-3 py-2 text-sm font-medium text-gray-700 hover:bg-gray-100"
          >
            Categories
          </a>
          <a
            href="/settings"
            className="flex items-center rounded-md px-3 py-2 text-sm font-medium text-gray-700 hover:bg-gray-100"
          >
            Settings
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
    path: '/',
    element: <AdminLayout />,
    children: [
      {
        index: true,
        element: <DashboardPage />,
      },
    ],
  },
]);
