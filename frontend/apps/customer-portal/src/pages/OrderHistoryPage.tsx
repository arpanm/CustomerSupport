import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Badge, Card, CardContent, CardHeader, CardTitle, Spinner } from '@supporthub/ui';
import { useAuthStore } from '../store/authStore.js';
import { getApiClient } from '../api/client.js';

interface OrderItem {
  productName: string;
  qty: number;
  price: number;
}

interface Order {
  orderId: string;
  status: string;
  totalAmount: number;
  createdAt: string;
  items: OrderItem[];
}

type OrderStatusVariant = 'default' | 'destructive' | 'secondary' | 'outline';

function getStatusVariant(status: string): OrderStatusVariant {
  const normalised = status.toUpperCase();
  if (normalised === 'DELIVERED') return 'secondary';
  if (normalised === 'CANCELLED' || normalised === 'FAILED') return 'destructive';
  if (normalised === 'PENDING' || normalised === 'PROCESSING') return 'outline';
  return 'default';
}

function formatDate(iso: string): string {
  return new Date(iso).toLocaleDateString('en-IN', {
    day: '2-digit',
    month: 'short',
    year: 'numeric',
  });
}

function formatCurrency(amount: number): string {
  return new Intl.NumberFormat('en-IN', {
    style: 'currency',
    currency: 'INR',
    maximumFractionDigits: 2,
  }).format(amount);
}

function isOrder(value: unknown): value is Order {
  if (typeof value !== 'object' || value === null) return false;
  const obj = value as Record<string, unknown>;
  return typeof obj['orderId'] === 'string';
}

function OrderRow({ order }: { order: Order }) {
  const [expanded, setExpanded] = useState(false);

  return (
    <div className="overflow-hidden rounded-lg border border-gray-200 bg-white shadow-sm">
      <button
        type="button"
        className="flex w-full items-center justify-between px-4 py-3 text-left hover:bg-gray-50 transition-colors"
        onClick={() => setExpanded((prev) => !prev)}
        aria-expanded={expanded}
      >
        <div className="flex items-center gap-4">
          <span className="font-mono text-sm text-blue-600">{order.orderId}</span>
          <Badge variant={getStatusVariant(order.status)}>{order.status}</Badge>
        </div>
        <div className="flex items-center gap-4">
          <span className="text-sm font-medium text-gray-900">
            {formatCurrency(order.totalAmount)}
          </span>
          <span className="text-xs text-gray-500">{formatDate(order.createdAt)}</span>
          <span className="text-gray-400 text-sm">{expanded ? '▲' : '▼'}</span>
        </div>
      </button>

      {expanded && (
        <div className="border-t border-gray-100 px-4 py-3">
          {order.items.length === 0 ? (
            <p className="text-sm text-gray-500">No item details available.</p>
          ) : (
            <table className="w-full text-sm">
              <thead>
                <tr className="text-left text-xs uppercase text-gray-500">
                  <th className="pb-2 pr-4">Product</th>
                  <th className="pb-2 pr-4">Qty</th>
                  <th className="pb-2 text-right">Price</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-50">
                {order.items.map((item, idx) => (
                  <tr key={`${order.orderId}-item-${idx}`}>
                    <td className="py-1.5 pr-4 text-gray-900">{item.productName}</td>
                    <td className="py-1.5 pr-4 text-gray-600">{item.qty}</td>
                    <td className="py-1.5 text-right text-gray-900">
                      {formatCurrency(item.price)}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      )}
    </div>
  );
}

export function OrderHistoryPage() {
  const tenantId = useAuthStore((s) => s.tenantId) ?? '';
  const client = getApiClient(tenantId);

  const { data: rawData, isLoading, isError, error } = useQuery({
    queryKey: ['orders'],
    queryFn: () => client.getOrderHistory(),
  });

  const orders: Order[] = (rawData ?? []).filter(isOrder);

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h2 className="text-2xl font-bold text-gray-900">Order History</h2>
        <p className="mt-1 text-sm text-gray-600">
          View all your past orders and their details.
        </p>
      </div>

      {isLoading && (
        <div className="flex justify-center py-12">
          <Spinner size="lg" label="Loading orders..." />
        </div>
      )}

      {isError && (
        <Card>
          <CardContent className="py-8 text-center">
            <p className="text-red-600">
              {error instanceof Error ? error.message : 'Failed to load orders.'}
            </p>
          </CardContent>
        </Card>
      )}

      {!isLoading && !isError && orders.length === 0 && (
        <Card>
          <CardHeader>
            <CardTitle className="text-center text-base text-gray-500">No orders found</CardTitle>
          </CardHeader>
        </Card>
      )}

      {!isLoading && !isError && orders.length > 0 && (
        <div className="flex flex-col gap-3">
          {orders.map((order) => (
            <OrderRow key={order.orderId} order={order} />
          ))}
        </div>
      )}
    </div>
  );
}
