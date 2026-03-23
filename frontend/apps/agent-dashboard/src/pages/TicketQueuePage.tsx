import { useQuery } from '@tanstack/react-query';
import { Card, CardContent, CardHeader, CardTitle, TicketStatusBadge, Spinner, Badge } from '@supporthub/ui';
import { useAuthStore } from '../store/authStore.js';

interface TicketSummary {
  id: string;
  ticketNumber: string;
  title: string;
  status: string;
  priority: string;
  customerName: string;
  assignedAgentId: string | null;
  createdAt: string;
  updatedAt: string;
}

interface TicketsResponse {
  data: TicketSummary[];
  pagination: {
    cursor?: string;
    hasMore: boolean;
    total?: number;
    limit: number;
  };
}

const PRIORITY_VARIANT: Record<string, 'default' | 'destructive' | 'secondary' | 'outline'> = {
  URGENT: 'destructive',
  HIGH: 'destructive',
  MEDIUM: 'default',
  LOW: 'secondary',
};

function useTicketQueue() {
  const token = useAuthStore((state) => state.token);

  return useQuery<TicketsResponse>({
    queryKey: ['tickets', 'queue'],
    queryFn: async () => {
      const response = await fetch(
        `${import.meta.env.VITE_API_BASE_URL}/api/v1/tickets?limit=25&sort=createdAt&direction=desc`,
        {
          headers: {
            Authorization: `Bearer ${token ?? ''}`,
            'X-Tenant-ID': import.meta.env.VITE_TENANT_ID,
          },
        },
      );

      if (!response.ok) {
        throw new Error(`Failed to fetch tickets: ${response.status.toString()}`);
      }

      return response.json() as Promise<TicketsResponse>;
    },
    enabled: token !== null,
    staleTime: 30_000,
    refetchInterval: 60_000,
  });
}

export function TicketQueuePage() {
  const { data, isLoading, isError, error } = useTicketQueue();

  if (isLoading) {
    return (
      <div className="flex h-64 items-center justify-center">
        <Spinner size="lg" label="Loading ticket queue..." />
      </div>
    );
  }

  if (isError) {
    return (
      <div className="rounded-md border border-red-200 bg-red-50 px-4 py-6 text-center text-red-700">
        <p className="font-medium">Failed to load tickets</p>
        <p className="mt-1 text-sm">{error instanceof Error ? error.message : 'Unknown error'}</p>
      </div>
    );
  }

  const tickets = data?.data ?? [];

  return (
    <div className="flex flex-col gap-4">
      <div className="flex items-center justify-between">
        <h2 className="text-2xl font-bold text-gray-900">Ticket Queue</h2>
        <span className="text-sm text-gray-500">
          {tickets.length} ticket{tickets.length !== 1 ? 's' : ''}
        </span>
      </div>

      {tickets.length === 0 ? (
        <Card>
          <CardContent className="py-12 text-center text-gray-500">
            <p className="text-lg font-medium">No tickets in queue</p>
            <p className="mt-1 text-sm">All caught up!</p>
          </CardContent>
        </Card>
      ) : (
        <div className="flex flex-col gap-3">
          {tickets.map((ticket) => (
            <Card key={ticket.id} className="cursor-pointer transition-shadow hover:shadow-md">
              <CardHeader className="pb-2">
                <div className="flex items-start justify-between gap-4">
                  <div className="min-w-0 flex-1">
                    <CardTitle className="truncate text-base">{ticket.title}</CardTitle>
                    <p className="mt-0.5 text-xs text-gray-500">{ticket.ticketNumber}</p>
                  </div>
                  <div className="flex shrink-0 items-center gap-2">
                    <Badge variant={PRIORITY_VARIANT[ticket.priority] ?? 'secondary'}>
                      {ticket.priority}
                    </Badge>
                    <TicketStatusBadge status={ticket.status} />
                  </div>
                </div>
              </CardHeader>
              <CardContent className="pt-0">
                <div className="flex items-center justify-between text-xs text-gray-500">
                  <span>Customer: {ticket.customerName}</span>
                  <span>{new Date(ticket.createdAt).toLocaleDateString('en-IN')}</span>
                </div>
              </CardContent>
            </Card>
          ))}
        </div>
      )}
    </div>
  );
}
