import { createBrowserRouter, Outlet } from 'react-router-dom';
import { HomePage } from './pages/HomePage.js';
import { CreateTicketPage } from './pages/CreateTicketPage.js';

function RootLayout() {
  return (
    <div className="min-h-screen bg-gray-50">
      <header className="border-b border-gray-200 bg-white shadow-sm">
        <div className="mx-auto max-w-4xl px-4 py-4">
          <h1 className="text-xl font-bold text-blue-600">SupportHub</h1>
        </div>
      </header>
      <main className="mx-auto max-w-4xl px-4 py-8">
        <Outlet />
      </main>
    </div>
  );
}

export const router = createBrowserRouter([
  {
    path: '/',
    element: <RootLayout />,
    children: [
      {
        index: true,
        element: <HomePage />,
      },
      {
        path: 'tickets/new',
        element: <CreateTicketPage />,
      },
    ],
  },
]);
