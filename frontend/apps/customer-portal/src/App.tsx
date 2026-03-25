import { createBrowserRouter, Navigate } from 'react-router-dom';
import { Layout } from './components/Layout.js';
import { ProtectedRoute } from './components/ProtectedRoute.js';
import { LoginPage } from './pages/LoginPage.js';
import { HomePage } from './pages/HomePage.js';
import { TicketListPage } from './pages/TicketListPage.js';
import { TicketDetailPage } from './pages/TicketDetailPage.js';
import { CreateTicketPage } from './pages/CreateTicketPage.js';
import { FAQSearchPage } from './pages/FAQSearchPage.js';
import { OrderHistoryPage } from './pages/OrderHistoryPage.js';
import { NotificationsPage } from './pages/NotificationsPage.js';

export const router = createBrowserRouter([
  {
    path: '/login',
    element: <LoginPage />,
  },
  {
    element: <ProtectedRoute />,
    children: [
      {
        element: <Layout />,
        children: [
          {
            path: '/',
            element: <HomePage />,
          },
          {
            path: '/tickets',
            element: <TicketListPage />,
          },
          {
            path: '/tickets/new',
            element: <CreateTicketPage />,
          },
          {
            path: '/tickets/:id',
            element: <TicketDetailPage />,
          },
          {
            path: '/faq',
            element: <FAQSearchPage />,
          },
          {
            path: '/orders',
            element: <OrderHistoryPage />,
          },
          {
            path: '/notifications',
            element: <NotificationsPage />,
          },
        ],
      },
    ],
  },
  {
    path: '*',
    element: <Navigate to="/tickets" replace />,
  },
]);
