import { useState, useEffect } from 'react';
import { useParams, Link } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { Card, CardContent, CardHeader, CardTitle, TicketStatusBadge, Spinner, Badge } from '@supporthub/ui';
import { useAuthStore } from '../store/authStore.js';
import { useWebSocketStore } from '../store/websocketStore.js';
import { AIAssistancePanel } from '../components/AIAssistancePanel.js';
import {
  fetchTicketDetail,
  fetchActivities,
  addComment,
  updateTicketStatus,
} from '../api/ticketApi.js';
import type { TicketActivity } from '../api/ticketApi.js';

const PRIORITY_VARIANT: Record<string, 'default' | 'destructive' | 'secondary' | 'outline'> = {
  URGENT: 'destructive',
  HIGH: 'destructive',
  MEDIUM: 'default',
  LOW: 'secondary',
};

function ActivityItem({ activity }: { activity: TicketActivity }) {
  const isAgent = activity.actorType === 'AGENT';
  return (
    <div className={`flex gap-3 ${isAgent ? 'flex-row-reverse' : 'flex-row'}`}>
      <div
        className={`h-8 w-8 shrink-0 rounded-full flex items-center justify-center text-xs font-bold ${
          isAgent ? 'bg-blue-100 text-blue-700' : 'bg-gray-100 text-gray-700'
        }`}
      >
        {activity.actorName.charAt(0).toUpperCase()}
      </div>
      <div className={`max-w-[70%] ${isAgent ? 'items-end' : 'items-start'} flex flex-col gap-1`}>
        <div
          className={`rounded-lg px-4 py-2 text-sm ${
            activity.isInternal
              ? 'border border-yellow-200 bg-yellow-50 text-yellow-800'
              : isAgent
                ? 'bg-blue-600 text-white'
                : 'bg-gray-100 text-gray-900'
          }`}
        >
          {activity.isInternal && (
            <span className="mb-1 block text-xs font-medium text-yellow-600">Internal note</span>
          )}
          {activity.content}
        </div>
        <span className="text-xs text-gray-400">
          {activity.actorName} • {new Date(activity.createdAt).toLocaleString('en-IN')}
        </span>
      </div>
    </div>
  );
}

async function assignTicket(token: string, ticketId: string, assigneeId: string): Promise<void> {
  const res = await fetch(`/api/v1/tickets/${encodeURIComponent(ticketId)}`, {
    method: 'PUT',
    headers: {
      Authorization: `Bearer ${token}`,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ assigneeId }),
  });
  if (!res.ok) {
    throw new Error(`Failed to assign ticket: ${res.status}`);
  }
}

export function TicketDetailPage() {
  const { ticketNumber } = useParams<{ ticketNumber: string }>();
  const { token, user } = useAuthStore();
  const { lastTicketUpdate, connect } = useWebSocketStore();
  const queryClient = useQueryClient();

  const [replyContent, setReplyContent] = useState('');
  const [isInternal, setIsInternal] = useState(false);

  const safeToken = token ?? '';
  const safeTicketNumber = ticketNumber ?? '';

  // Connect to STOMP WebSocket on mount
  useEffect(() => {
    if (token != null && user?.tenantId != null) {
      connect(user.tenantId, token);
    }
  }, [token, user?.tenantId, connect]);

  // Invalidate ticket and activities queries on relevant STOMP updates
  useEffect(() => {
    if (lastTicketUpdate == null) return;
    void queryClient.invalidateQueries({ queryKey: ['ticket', safeTicketNumber] });
    void queryClient.invalidateQueries({ queryKey: ['ticket', safeTicketNumber, 'activities'] });
  }, [lastTicketUpdate, safeTicketNumber, queryClient]);

  const { data: ticket, isLoading: ticketLoading } = useQuery({
    queryKey: ['ticket', safeTicketNumber],
    queryFn: () => fetchTicketDetail(safeToken, safeTicketNumber),
    enabled: safeTicketNumber.length > 0 && safeToken.length > 0,
    staleTime: 30_000,
  });

  const { data: activities, isLoading: activitiesLoading } = useQuery({
    queryKey: ['ticket', safeTicketNumber, 'activities'],
    queryFn: () => fetchActivities(safeToken, safeTicketNumber),
    enabled: safeTicketNumber.length > 0 && safeToken.length > 0,
    refetchInterval: 30_000,
  });

  const addCommentMutation = useMutation({
    mutationFn: (vars: { content: string; isInternal: boolean }) =>
      addComment(safeToken, safeTicketNumber, vars.content, vars.isInternal),
    onSuccess: () => {
      setReplyContent('');
      void queryClient.invalidateQueries({ queryKey: ['ticket', safeTicketNumber, 'activities'] });
    },
  });

  const statusMutation = useMutation({
    mutationFn: (action: 'resolve' | 'escalate' | 'reopen') =>
      updateTicketStatus(safeToken, safeTicketNumber, action),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['ticket', safeTicketNumber] });
      void queryClient.invalidateQueries({ queryKey: ['tickets', 'queue'] });
    },
  });

  const assignMutation = useMutation({
    mutationFn: () => {
      if (ticket == null || user == null) throw new Error('No ticket or user');
      return assignTicket(safeToken, ticket.id, user.id);
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['ticket', safeTicketNumber] });
    },
  });

  const handleSubmitReply = () => {
    if (replyContent.trim().length === 0) return;
    addCommentMutation.mutate({ content: replyContent, isInternal });
  };

  if (ticketLoading) {
    return (
      <div className="flex h-64 items-center justify-center">
        <Spinner size="lg" label="Loading ticket..." />
      </div>
    );
  }

  if (ticket == null) {
    return (
      <div className="py-12 text-center text-gray-500">
        <p>Ticket not found</p>
        <Link to="/" className="mt-2 text-sm text-blue-600 hover:underline">
          Back to queue
        </Link>
      </div>
    );
  }

  const canResolve = ['OPEN', 'IN_PROGRESS', 'PENDING_CUSTOMER_RESPONSE'].includes(ticket.status);
  const canEscalate = ['OPEN', 'IN_PROGRESS'].includes(ticket.status);
  const canReopen = ['RESOLVED', 'CLOSED'].includes(ticket.status);
  const isAssignedToMe = ticket.assignedAgentId != null && user?.id != null && ticket.assignedAgentId === user.id;

  return (
    <div className="flex flex-col gap-4">
      {/* Header */}
      <div className="flex items-start justify-between gap-4">
        <div className="min-w-0 flex-1">
          <Link to="/" className="text-sm text-blue-600 hover:underline">
            Back to queue
          </Link>
          <h2 className="mt-1 text-xl font-bold text-gray-900">{ticket.title}</h2>
          <div className="mt-1 flex items-center gap-3 text-sm text-gray-500">
            <span>{ticket.ticketNumber}</span>
            <span>•</span>
            <span>{ticket.categoryName}</span>
            <span>•</span>
            <span>{ticket.channel}</span>
          </div>
        </div>
        <div className="flex shrink-0 items-center gap-2">
          <Badge variant={PRIORITY_VARIANT[ticket.priority] ?? 'secondary'}>{ticket.priority}</Badge>
          <TicketStatusBadge status={ticket.status} />
        </div>
      </div>

      {/* SLA warning */}
      {ticket.slaBreached && (
        <div className="rounded-md border border-red-200 bg-red-50 px-4 py-2 text-sm text-red-700">
          SLA has been breached
        </div>
      )}

      <div className="grid grid-cols-1 gap-4 lg:grid-cols-3">
        {/* Main content */}
        <div className="flex flex-col gap-4 lg:col-span-2">
          {/* Description */}
          <Card>
            <CardHeader>
              <CardTitle className="text-sm">Description</CardTitle>
            </CardHeader>
            <CardContent>
              <p className="text-sm text-gray-700 whitespace-pre-wrap">{ticket.description}</p>
              {ticket.orderId != null && (
                <p className="mt-2 text-xs text-gray-500">Order ID: {ticket.orderId}</p>
              )}
            </CardContent>
          </Card>

          {/* Conversation */}
          <Card>
            <CardHeader>
              <CardTitle className="text-sm">Conversation</CardTitle>
            </CardHeader>
            <CardContent>
              {activitiesLoading ? (
                <Spinner size="sm" label="Loading..." />
              ) : (
                <div className="flex flex-col gap-4">
                  {(activities ?? []).map((activity) => (
                    <ActivityItem key={activity.id} activity={activity} />
                  ))}
                </div>
              )}

              {/* Reply box */}
              <div className="mt-4 flex flex-col gap-2 border-t border-gray-100 pt-4">
                <div className="flex items-center gap-3">
                  <label className="flex items-center gap-1.5 text-xs text-gray-600">
                    <input
                      type="checkbox"
                      checked={isInternal}
                      onChange={(e) => { setIsInternal(e.target.checked); }}
                      className="rounded border-gray-300"
                    />
                    Internal note
                  </label>
                </div>
                <textarea
                  value={replyContent}
                  onChange={(e) => { setReplyContent(e.target.value); }}
                  placeholder={isInternal ? 'Add an internal note (not visible to customer)...' : 'Type your reply to the customer...'}
                  rows={3}
                  className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                />
                <div className="flex items-center justify-between">
                  <div className="flex gap-2">
                    {canResolve && (
                      <button
                        onClick={() => { statusMutation.mutate('resolve'); }}
                        disabled={statusMutation.isPending}
                        className="rounded-md bg-green-600 px-3 py-1.5 text-xs font-medium text-white hover:bg-green-700 disabled:opacity-50"
                      >
                        Resolve
                      </button>
                    )}
                    {canEscalate && (
                      <button
                        onClick={() => { statusMutation.mutate('escalate'); }}
                        disabled={statusMutation.isPending}
                        className="rounded-md bg-orange-500 px-3 py-1.5 text-xs font-medium text-white hover:bg-orange-600 disabled:opacity-50"
                      >
                        Escalate
                      </button>
                    )}
                    {canReopen && (
                      <button
                        onClick={() => { statusMutation.mutate('reopen'); }}
                        disabled={statusMutation.isPending}
                        className="rounded-md bg-gray-600 px-3 py-1.5 text-xs font-medium text-white hover:bg-gray-700 disabled:opacity-50"
                      >
                        Reopen
                      </button>
                    )}
                  </div>
                  <button
                    onClick={handleSubmitReply}
                    disabled={replyContent.trim().length === 0 || addCommentMutation.isPending}
                    className="rounded-md bg-blue-600 px-4 py-1.5 text-sm font-medium text-white hover:bg-blue-700 disabled:opacity-50"
                  >
                    {addCommentMutation.isPending ? 'Sending...' : 'Send Reply'}
                  </button>
                </div>
              </div>
            </CardContent>
          </Card>
        </div>

        {/* Sidebar */}
        <div className="flex flex-col gap-4">
          {/* Ticket info */}
          <Card>
            <CardHeader>
              <div className="flex items-center justify-between">
                <CardTitle className="text-sm">Ticket Info</CardTitle>
                {!isAssignedToMe && (
                  <button
                    onClick={() => { assignMutation.mutate(); }}
                    disabled={assignMutation.isPending}
                    className="rounded-md bg-blue-50 px-2 py-1 text-xs font-medium text-blue-700 hover:bg-blue-100 disabled:opacity-50"
                  >
                    {assignMutation.isPending ? 'Assigning...' : 'Assign to me'}
                  </button>
                )}
              </div>
            </CardHeader>
            <CardContent className="flex flex-col gap-2 text-sm">
              <div className="flex justify-between">
                <span className="text-gray-500">Customer</span>
                <span className="font-medium">{ticket.customerName}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-gray-500">Assigned</span>
                <span className="font-medium">
                  {isAssignedToMe ? 'You' : (ticket.assignedAgentId ?? 'Unassigned')}
                </span>
              </div>
              {ticket.sentimentLabel != null && (
                <div className="flex justify-between">
                  <span className="text-gray-500">Sentiment</span>
                  <span className="font-medium capitalize">{ticket.sentimentLabel.replace(/_/g, ' ')}</span>
                </div>
              )}
              <div className="flex justify-between">
                <span className="text-gray-500">Created</span>
                <span>{new Date(ticket.createdAt).toLocaleDateString('en-IN')}</span>
              </div>
              {ticket.resolutionDeadline != null && (
                <div className="flex justify-between">
                  <span className="text-gray-500">SLA Deadline</span>
                  <span className={new Date(ticket.resolutionDeadline) < new Date() ? 'text-red-600 font-medium' : ''}>
                    {new Date(ticket.resolutionDeadline).toLocaleDateString('en-IN')}
                  </span>
                </div>
              )}
            </CardContent>
          </Card>

          {/* AI Assistance Panel */}
          {user?.role != null && (
            <AIAssistancePanel
              ticketId={ticket.id}
              token={safeToken}
              sentiment={ticket.sentimentLabel ?? undefined}
              onApply={(text) => {
                setReplyContent(text);
                setIsInternal(false);
              }}
            />
          )}
        </div>
      </div>
    </div>
  );
}
