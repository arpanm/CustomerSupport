import { useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { Badge, Card, CardContent, Spinner, TicketStatusBadge } from '@supporthub/ui';
import type { TicketStatus, Priority } from '@supporthub/customer-sdk';
import { useAuthStore } from '../store/authStore.js';
import { getApiClient } from '../api/client.js';

const PRIORITY_VARIANT: Record<Priority, 'default' | 'destructive' | 'secondary' | 'outline'> = {
  LOW: 'secondary',
  MEDIUM: 'outline',
  HIGH: 'default',
  URGENT: 'destructive',
};

const STATUS_FILTERS: Array<{ label: string; value: string }> = [
  { label: 'All', value: 'ALL' },
  { label: 'Open', value: 'OPEN' },
  { label: 'In Progress', value: 'IN_PROGRESS' },
  { label: 'Resolved', value: 'RESOLVED' },
];

function formatDate(iso: string): string {
  return new Date(iso).toLocaleDateString('en-IN', {
    day: '2-digit',
    month: 'short',
    year: 'numeric',
  });
}

export function TicketListPage() {
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const tenantId = useAuthStore((s) => s.tenantId) ?? '';
  const client = getApiClient(tenantId);

  const statusFilter = searchParams.get('status') ?? 'ALL';
  const [cursor, setCursor] = useState<string | undefined>(undefined);
  const [cursorHistory, setCursorHistory] = useState<Array<string | undefined>>([undefined]);
  const [pageIndex, setPageIndex] = useState(0);

  const { data, isLoading, isError, error } = useQuery({
    queryKey: ['tickets', statusFilter, cursor],
    queryFn: () => client.listMyTickets(cursor, 20),
  });

  const tickets = data?.data ?? [];
  const filteredTickets =
    statusFilter === 'ALL'
      ? tickets
      : tickets.filter((t) => t.status === (statusFilter as TicketStatus));

  function handleStatusFilter(status: string) {
    setSearchParams(status === 'ALL' ? {} : { status });
    setCursor(undefined);
    setCursorHistory([undefined]);
    setPageIndex(0);
  }

  function handleNext() {
    if (data?.cursor !== undefined) {
      const newHistory = [...cursorHistory, data.cursor];
      setCursorHistory(newHistory);
      setPageIndex(pageIndex + 1);
      setCursor(data.cursor);
    }
  }

  function handlePrev() {
    if (pageIndex > 0) {
      const newIndex = pageIndex - 1;
      setPageIndex(newIndex);
      setCursor(cursorHistory[newIndex]);
    }
  }

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center justify-between">
        <h2 className="text-2xl font-bold text-gray-900">My Tickets</h2>
      </div>

      <div className="flex gap-2 flex-wrap">
        {STATUS_FILTERS.map((f) => (
          <button
            key={f.value}
            onClick={() => handleStatusFilter(f.value)}
            className={`rounded-full px-4 py-1.5 text-sm font-medium transition-colors ${
              statusFilter === f.value
                ? 'bg-blue-600 text-white'
                : 'bg-gray-100 text-gray-700 hover:bg-gray-200'
            }`}
          >
            {f.label}
          </button>
        ))}
      </div>

      {isLoading && (
        <div className="flex justify-center py-12">
          <Spinner size="lg" label="Loading tickets..." />
        </div>
      )}

      {isError && (
        <Card>
          <CardContent className="py-8 text-center">
            <p className="text-red-600">
              {error instanceof Error ? error.message : 'Failed to load tickets.'}
            </p>
          </CardContent>
        </Card>
      )}

      {!isLoading && !isError && filteredTickets.length === 0 && (
        <Card>
          <CardContent className="py-12 text-center">
            <p className="text-gray-500">No tickets found.</p>
          </CardContent>
        </Card>
      )}

      {!isLoading && !isError && filteredTickets.length > 0 && (
        <div className="overflow-hidden rounded-lg border border-gray-200 bg-white shadow-sm">
          <table className="w-full text-sm">
            <thead className="bg-gray-50 text-xs uppercase text-gray-500">
              <tr>
                <th className="px-4 py-3 text-left">Ticket #</th>
                <th className="px-4 py-3 text-left">Subject</th>
                <th className="px-4 py-3 text-left">Status</th>
                <th className="px-4 py-3 text-left">Priority</th>
                <th className="px-4 py-3 text-left">Created</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {filteredTickets.map((ticket) => (
                <tr
                  key={ticket.id}
                  className="cursor-pointer hover:bg-gray-50 transition-colors"
                  onClick={() => {
                    void navigate(`/tickets/${ticket.ticketNumber}`);
                  }}
                >
                  <td className="px-4 py-3 font-mono text-blue-600">{ticket.ticketNumber}</td>
                  <td className="px-4 py-3 text-gray-900 max-w-xs truncate">{ticket.title}</td>
                  <td className="px-4 py-3">
                    <TicketStatusBadge status={ticket.status} />
                  </td>
                  <td className="px-4 py-3">
                    <Badge variant={PRIORITY_VARIANT[ticket.priority]}>{ticket.priority}</Badge>
                  </td>
                  <td className="px-4 py-3 text-gray-500">{formatDate(ticket.createdAt)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {!isLoading && !isError && (data?.hasMore === true || pageIndex > 0) && (
        <div className="flex items-center justify-between">
          <button
            onClick={handlePrev}
            disabled={pageIndex === 0}
            className="rounded-md border border-gray-300 px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50 disabled:cursor-not-allowed disabled:opacity-50"
          >
            Previous
          </button>
          <span className="text-sm text-gray-500">Page {pageIndex + 1}</span>
          <button
            onClick={handleNext}
            disabled={data?.hasMore !== true}
            className="rounded-md border border-gray-300 px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50 disabled:cursor-not-allowed disabled:opacity-50"
          >
            Next
          </button>
        </div>
      )}
    </div>
  );
}
