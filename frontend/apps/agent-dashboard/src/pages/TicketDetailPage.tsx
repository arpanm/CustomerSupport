import { useRef, useState, useEffect } from 'react';
import { Link, useParams } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useAuthStore } from '../store/authStore.js';
import {
  fetchTicketDetail,
  fetchActivities,
  addComment,
  updateTicketStatus,
  fetchAiSuggestions,
  fetchCustomerProfile,
  fetchRelatedTickets,
} from '../api/ticketApi.js';
import type { TicketDetail, TicketActivity, ResolutionSuggestion, CustomerProfile, TicketSummary } from '../api/ticketApi.js';

// ---------------------------------------------------------------------------
// Icon helper
// ---------------------------------------------------------------------------

function Icon({ d, className = 'w-4 h-4' }: { d: string; className?: string }) {
  return (
    <svg
      xmlns="http://www.w3.org/2000/svg"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={2}
      strokeLinecap="round"
      strokeLinejoin="round"
      className={className}
    >
      <path d={d} />
    </svg>
  );
}

// ---------------------------------------------------------------------------
// Utility functions
// ---------------------------------------------------------------------------

function timeAgo(iso: string): string {
  const diff = Date.now() - new Date(iso).getTime();
  const seconds = Math.floor(diff / 1000);
  if (seconds < 60) return `${seconds}s ago`;
  const minutes = Math.floor(seconds / 60);
  if (minutes < 60) return `${minutes}m ago`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours}h ago`;
  const days = Math.floor(hours / 24);
  if (days < 30) return `${days}d ago`;
  const months = Math.floor(days / 30);
  if (months < 12) return `${months}mo ago`;
  return `${Math.floor(months / 12)}y ago`;
}

function formatDate(iso: string): string {
  return new Date(iso).toLocaleString('en-IN', {
    day: '2-digit',
    month: 'short',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
}

// ---------------------------------------------------------------------------
// Constants
// ---------------------------------------------------------------------------

const PRIORITY_COLOR: Record<string, string> = {
  CRITICAL: 'bg-red-100 text-red-800 border border-red-200',
  HIGH: 'bg-orange-100 text-orange-800 border border-orange-200',
  MEDIUM: 'bg-yellow-100 text-yellow-800 border border-yellow-200',
  LOW: 'bg-green-100 text-green-800 border border-green-200',
};

const STATUS_COLOR: Record<string, string> = {
  OPEN: 'bg-blue-100 text-blue-800 border border-blue-200',
  IN_PROGRESS: 'bg-indigo-100 text-indigo-800 border border-indigo-200',
  ESCALATED: 'bg-red-100 text-red-800 border border-red-200',
  RESOLVED: 'bg-green-100 text-green-800 border border-green-200',
  CLOSED: 'bg-gray-100 text-gray-700 border border-gray-200',
  REOPENED: 'bg-purple-100 text-purple-800 border border-purple-200',
};

const SENTIMENT_COLOR: Record<string, string> = {
  POSITIVE: 'bg-green-100 text-green-800',
  NEGATIVE: 'bg-red-100 text-red-800',
  NEUTRAL: 'bg-gray-100 text-gray-700',
};

// ---------------------------------------------------------------------------
// ActivityItem component
// ---------------------------------------------------------------------------

function ActivityItem({ activity }: { activity: TicketActivity }) {
  if (activity.activityType === 'STATUS_CHANGE') {
    return (
      <div className="flex justify-center my-3">
        <span className="text-xs text-gray-400 bg-gray-100 rounded-full px-3 py-1 border border-gray-200">
          {activity.content} &mdash; {timeAgo(activity.createdAt)}
        </span>
      </div>
    );
  }

  if (activity.isInternal) {
    return (
      <div className="flex justify-end my-2">
        <div className="max-w-[75%]">
          <div className="flex items-center gap-1 mb-1 justify-end">
            <span className="text-xs font-semibold text-amber-700 bg-amber-100 rounded px-2 py-0.5 border border-amber-200">
              Internal Note
            </span>
            <span className="text-xs text-gray-400">{activity.actorName}</span>
          </div>
          <div className="bg-amber-50 border border-amber-200 rounded-xl rounded-tr-sm px-4 py-2.5 text-sm text-amber-900 shadow-sm whitespace-pre-wrap">
            {activity.content}
          </div>
          <div className="text-xs text-gray-400 text-right mt-1">{timeAgo(activity.createdAt)}</div>
        </div>
      </div>
    );
  }

  if (activity.actorType === 'CUSTOMER') {
    return (
      <div className="flex justify-start my-2">
        <div className="max-w-[75%]">
          <div className="flex items-center gap-1 mb-1">
            <span className="text-xs font-medium text-gray-600">{activity.actorName}</span>
            <span className="text-xs text-gray-400 ml-1">Customer</span>
          </div>
          <div className="bg-gray-100 border border-gray-200 rounded-xl rounded-tl-sm px-4 py-2.5 text-sm text-gray-800 shadow-sm whitespace-pre-wrap">
            {activity.content}
          </div>
          <div className="text-xs text-gray-400 mt-1">{timeAgo(activity.createdAt)}</div>
        </div>
      </div>
    );
  }

  // AGENT reply (not internal)
  return (
    <div className="flex justify-end my-2">
      <div className="max-w-[75%]">
        <div className="flex items-center gap-1 mb-1 justify-end">
          <span className="text-xs font-medium text-blue-700">{activity.actorName}</span>
          <span className="text-xs text-gray-400">Agent</span>
        </div>
        <div className="bg-blue-600 text-white rounded-xl rounded-tr-sm px-4 py-2.5 text-sm shadow-sm whitespace-pre-wrap">
          {activity.content}
        </div>
        <div className="text-xs text-gray-400 text-right mt-1">{timeAgo(activity.createdAt)}</div>
      </div>
    </div>
  );
}

// ---------------------------------------------------------------------------
// SlaCountdown component
// ---------------------------------------------------------------------------

function SlaCountdown({ deadline, label }: { deadline: string; label: string }) {
  const [now, setNow] = useState(Date.now());

  useEffect(() => {
    const id = setInterval(() => setNow(Date.now()), 10_000);
    return () => clearInterval(id);
  }, []);

  const diffMs = new Date(deadline).getTime() - now;
  const isOverdue = diffMs < 0;
  const isWarning = !isOverdue && diffMs < 60 * 60 * 1000;

  const abs = Math.abs(diffMs);
  const totalMinutes = Math.floor(abs / 60000);
  const hours = Math.floor(totalMinutes / 60);
  const minutes = totalMinutes % 60;
  const display = hours > 0 ? `${hours}h ${minutes}m` : `${minutes}m`;

  const colorClass = isOverdue
    ? 'text-red-700 font-semibold'
    : isWarning
    ? 'text-red-600 font-semibold'
    : 'text-gray-700';

  return (
    <div className="flex items-center gap-1 text-xs">
      <Icon
        d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z"
        className={`w-3.5 h-3.5 ${isOverdue || isWarning ? 'text-red-500' : 'text-gray-400'}`}
      />
      <span className="text-gray-500">{label}:</span>
      <span className={colorClass}>
        {isOverdue ? `Overdue by ${display}` : display}
      </span>
    </div>
  );
}

// ---------------------------------------------------------------------------
// Panel wrapper
// ---------------------------------------------------------------------------

function Panel({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div className="bg-white rounded-xl border border-gray-200 shadow-sm overflow-hidden mb-4">
      <div className="px-4 py-3 border-b border-gray-100 bg-gray-50">
        <h3 className="text-sm font-semibold text-gray-700 uppercase tracking-wide">{title}</h3>
      </div>
      <div className="p-4">{children}</div>
    </div>
  );
}

// ---------------------------------------------------------------------------
// Main page
// ---------------------------------------------------------------------------

export function TicketDetailPage() {
  const { ticketNumber } = useParams<{ ticketNumber: string }>();
  const { token } = useAuthStore();
  const queryClient = useQueryClient();

  const [replyContent, setReplyContent] = useState('');
  const [isInternal, setIsInternal] = useState(false);
  const conversationEndRef = useRef<HTMLDivElement>(null);

  // Queries
  const {
    data: ticket,
    isLoading: ticketLoading,
    isError: ticketError,
  } = useQuery<TicketDetail>({
    queryKey: ['ticket', ticketNumber],
    queryFn: () => fetchTicketDetail(token!, ticketNumber!),
    enabled: !!token && !!ticketNumber,
    refetchInterval: 30_000,
  });

  const { data: activities = [] } = useQuery<TicketActivity[]>({
    queryKey: ['activities', ticketNumber],
    queryFn: () => fetchActivities(token!, ticketNumber!),
    enabled: !!token && !!ticketNumber,
    refetchInterval: 15_000,
  });

  const { data: aiSuggestions = [] } = useQuery<ResolutionSuggestion[]>({
    queryKey: ['ai-suggestions', ticket?.id],
    queryFn: () =>
      fetchAiSuggestions(
        token!,
        ticket!.id,
        ticket!.title,
        ticket!.description,
        ticket!.categoryName,
      ),
    enabled: !!token && !!ticket,
    staleTime: 5 * 60 * 1000,
  });

  const { data: customerProfile } = useQuery<CustomerProfile>({
    queryKey: ['customer', ticket?.customerId],
    queryFn: () => fetchCustomerProfile(token!, ticket!.customerId),
    enabled: !!token && !!ticket?.customerId,
    staleTime: 5 * 60 * 1000,
  });

  const { data: relatedTickets = [] } = useQuery<TicketSummary[]>({
    queryKey: ['related-tickets', ticket?.customerId, ticketNumber],
    queryFn: () => fetchRelatedTickets(token!, ticket!.customerId, ticketNumber!),
    enabled: !!token && !!ticket?.customerId && !!ticketNumber,
    staleTime: 5 * 60 * 1000,
  });

  // Auto-scroll to bottom
  useEffect(() => {
    conversationEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [activities]);

  // Mutations
  const addCommentMutation = useMutation({
    mutationFn: () => addComment(token!, ticketNumber!, replyContent.trim(), isInternal),
    onSuccess: () => {
      setReplyContent('');
      queryClient.invalidateQueries({ queryKey: ['activities', ticketNumber] });
      queryClient.invalidateQueries({ queryKey: ['ticket', ticketNumber] });
    },
  });

  const statusMutation = useMutation({
    mutationFn: (action: 'resolve' | 'escalate' | 'reopen' | 'close') =>
      updateTicketStatus(token!, ticketNumber!, action),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['ticket', ticketNumber] });
      queryClient.invalidateQueries({ queryKey: ['activities', ticketNumber] });
    },
  });

  // Derived
  const status = ticket?.status ?? '';
  const isTerminal = status === 'RESOLVED' || status === 'CLOSED';

  function maskPhone(phone: string): string {
    const digits = phone.replace(/\D/g, '');
    if (digits.length < 4) return phone;
    const last4 = digits.slice(-4);
    const masked = digits.slice(0, -4).replace(/\d/g, '*');
    return `+91 ${masked.slice(0, 3)} ${masked.slice(3)}${last4}`;
  }

  // Loading / error states
  if (ticketLoading) {
    return (
      <div className="flex items-center justify-center h-full bg-gray-50">
        <div className="flex flex-col items-center gap-3">
          <div className="w-8 h-8 border-4 border-blue-500 border-t-transparent rounded-full animate-spin" />
          <span className="text-sm text-gray-500">Loading ticket…</span>
        </div>
      </div>
    );
  }

  if (ticketError || !ticket) {
    return (
      <div className="flex items-center justify-center h-full bg-gray-50">
        <div className="text-center">
          <p className="text-red-600 font-medium mb-2">Failed to load ticket</p>
          <Link to="/tickets" className="text-sm text-blue-600 hover:underline">
            Back to tickets
          </Link>
        </div>
      </div>
    );
  }

  return (
    <div className="flex h-screen overflow-hidden bg-gray-50 font-sans">
      {/* ================================================================
          LEFT COLUMN — Conversation
      ================================================================ */}
      <div className="flex flex-col" style={{ flex: '0 0 60%', minWidth: 0 }}>
        {/* Top bar */}
        <div className="flex-shrink-0 bg-white border-b border-gray-200 px-5 py-3 shadow-sm">
          <div className="flex items-start gap-3">
            {/* Back button */}
            <Link
              to="/tickets"
              className="mt-0.5 flex items-center gap-1 text-gray-400 hover:text-gray-700 transition-colors text-sm flex-shrink-0"
            >
              <Icon d="M15 19l-7-7 7-7" className="w-4 h-4" />
              Back
            </Link>

            {/* Title + badges */}
            <div className="flex-1 min-w-0">
              <div className="flex flex-wrap items-center gap-2 mb-1">
                <span className="font-mono text-xs font-bold text-gray-400 bg-gray-100 px-2 py-0.5 rounded border border-gray-200">
                  #{ticket.ticketNumber}
                </span>
                <span
                  className={`text-xs font-semibold px-2 py-0.5 rounded-full ${
                    STATUS_COLOR[ticket.status] ?? 'bg-gray-100 text-gray-700'
                  }`}
                >
                  {ticket.status.replace('_', ' ')}
                </span>
                <span
                  className={`text-xs font-semibold px-2 py-0.5 rounded-full ${
                    PRIORITY_COLOR[ticket.priority] ?? 'bg-gray-100 text-gray-700'
                  }`}
                >
                  {ticket.priority}
                </span>
                {ticket.slaBreached && (
                  <span className="flex items-center gap-1 text-xs font-bold text-red-700 bg-red-50 border border-red-200 px-2 py-0.5 rounded-full">
                    <Icon d="M12 9v2m0 4h.01M10.29 3.86L1.82 18a2 2 0 001.71 3h16.94a2 2 0 001.71-3L13.71 3.86a2 2 0 00-3.42 0z" className="w-3 h-3" />
                    SLA Breached
                  </span>
                )}
              </div>
              <h1 className="text-base font-semibold text-gray-900 leading-snug truncate">
                {ticket.title}
              </h1>
              <p className="text-xs text-gray-400 mt-0.5">
                {ticket.customerName} &bull; {ticket.categoryName}
                {ticket.subCategoryName ? ` / ${ticket.subCategoryName}` : ''}
                {ticket.orderId ? ` · Order #${ticket.orderId}` : ''}
              </p>
            </div>
          </div>
        </div>

        {/* Conversation */}
        <div className="flex-1 overflow-y-auto px-6 py-4 space-y-1">
          {activities.length === 0 && (
            <div className="flex justify-center items-center h-32 text-sm text-gray-400">
              No activity yet
            </div>
          )}
          {activities.map((activity) => (
            <ActivityItem key={activity.id} activity={activity} />
          ))}
          <div ref={conversationEndRef} />
        </div>

        {/* Reply composer */}
        <div className="flex-shrink-0 bg-white border-t border-gray-200 px-5 py-4 shadow-inner">
          <div
            className={`rounded-xl border-2 transition-colors ${
              isInternal
                ? 'border-amber-300 bg-amber-50'
                : 'border-gray-200 bg-white focus-within:border-blue-400'
            }`}
          >
            <textarea
              className={`w-full px-4 pt-3 pb-2 text-sm resize-none bg-transparent outline-none placeholder-gray-400 text-gray-800 min-h-[80px]`}
              placeholder={isInternal ? 'Write an internal note…' : 'Reply to customer…'}
              value={replyContent}
              onChange={(e) => setReplyContent(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === 'Enter' && (e.ctrlKey || e.metaKey) && replyContent.trim()) {
                  addCommentMutation.mutate();
                }
              }}
              rows={3}
            />
            <div className="flex items-center justify-between px-3 py-2 border-t border-gray-100">
              <button
                type="button"
                onClick={() => setIsInternal((v) => !v)}
                className={`flex items-center gap-1.5 text-xs font-medium px-3 py-1.5 rounded-lg transition-colors ${
                  isInternal
                    ? 'bg-amber-200 text-amber-800 border border-amber-300'
                    : 'bg-gray-100 text-gray-600 border border-gray-200 hover:bg-gray-200'
                }`}
              >
                <Icon d="M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8z" className="w-3.5 h-3.5" />
                {isInternal ? 'Internal Note (ON)' : 'Internal Note'}
              </button>

              <div className="flex items-center gap-2">
                <span className="text-xs text-gray-400">Ctrl+Enter to send</span>
                <button
                  type="button"
                  disabled={!replyContent.trim() || addCommentMutation.isPending}
                  onClick={() => addCommentMutation.mutate()}
                  className="flex items-center gap-1.5 px-4 py-1.5 bg-blue-600 hover:bg-blue-700 disabled:bg-blue-300 text-white text-sm font-medium rounded-lg transition-colors"
                >
                  {addCommentMutation.isPending ? (
                    <span className="w-4 h-4 border-2 border-white border-t-transparent rounded-full animate-spin inline-block" />
                  ) : (
                    <Icon d="M12 19l9 2-9-18-9 18 9-2zm0 0v-8" className="w-4 h-4" />
                  )}
                  Send
                </button>
              </div>
            </div>
          </div>

          {/* Action row */}
          <div className="flex flex-wrap gap-2 mt-3">
            {!isTerminal && (
              <button
                type="button"
                disabled={statusMutation.isPending}
                onClick={() => statusMutation.mutate('resolve')}
                className="flex items-center gap-1.5 px-3 py-1.5 bg-green-50 hover:bg-green-100 text-green-700 border border-green-200 text-sm font-medium rounded-lg transition-colors disabled:opacity-50"
              >
                <Icon d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" className="w-4 h-4" />
                Resolve
              </button>
            )}
            {status !== 'ESCALATED' && !isTerminal && (
              <button
                type="button"
                disabled={statusMutation.isPending}
                onClick={() => statusMutation.mutate('escalate')}
                className="flex items-center gap-1.5 px-3 py-1.5 bg-orange-50 hover:bg-orange-100 text-orange-700 border border-orange-200 text-sm font-medium rounded-lg transition-colors disabled:opacity-50"
              >
                <Icon d="M5 10l7-7m0 0l7 7m-7-7v18" className="w-4 h-4" />
                Escalate
              </button>
            )}
            {(status === 'OPEN' || status === 'IN_PROGRESS') && (
              <button
                type="button"
                disabled={statusMutation.isPending}
                onClick={() => statusMutation.mutate('close')}
                className="flex items-center gap-1.5 px-3 py-1.5 bg-gray-50 hover:bg-gray-100 text-gray-600 border border-gray-200 text-sm font-medium rounded-lg transition-colors disabled:opacity-50"
              >
                <Icon d="M6 18L18 6M6 6l12 12" className="w-4 h-4" />
                Close
              </button>
            )}
            {(status === 'RESOLVED' || status === 'CLOSED') && (
              <button
                type="button"
                disabled={statusMutation.isPending}
                onClick={() => statusMutation.mutate('reopen')}
                className="flex items-center gap-1.5 px-3 py-1.5 bg-purple-50 hover:bg-purple-100 text-purple-700 border border-purple-200 text-sm font-medium rounded-lg transition-colors disabled:opacity-50"
              >
                <Icon d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" className="w-4 h-4" />
                Reopen
              </button>
            )}
            {statusMutation.isError && (
              <span className="text-xs text-red-600 self-center">Action failed. Try again.</span>
            )}
          </div>
        </div>
      </div>

      {/* ================================================================
          RIGHT SIDEBAR
      ================================================================ */}
      <div
        className="flex-1 overflow-y-auto border-l border-gray-200 bg-gray-50 px-4 py-4"
        style={{ minWidth: 0 }}
      >
        {/* Ticket Details */}
        <Panel title="Ticket Details">
          <dl className="space-y-2.5 text-sm">
            <div className="flex items-center justify-between">
              <dt className="text-gray-500">Status</dt>
              <dd>
                <span
                  className={`text-xs font-semibold px-2 py-0.5 rounded-full ${
                    STATUS_COLOR[ticket.status] ?? 'bg-gray-100 text-gray-700'
                  }`}
                >
                  {ticket.status.replace('_', ' ')}
                </span>
              </dd>
            </div>
            <div className="flex items-center justify-between">
              <dt className="text-gray-500">Priority</dt>
              <dd>
                <span
                  className={`text-xs font-semibold px-2 py-0.5 rounded-full ${
                    PRIORITY_COLOR[ticket.priority] ?? 'bg-gray-100 text-gray-700'
                  }`}
                >
                  {ticket.priority}
                </span>
              </dd>
            </div>
            <div className="flex items-center justify-between">
              <dt className="text-gray-500">Channel</dt>
              <dd className="font-medium text-gray-700">{ticket.channel}</dd>
            </div>
            <div className="flex items-center justify-between">
              <dt className="text-gray-500">Category</dt>
              <dd className="font-medium text-gray-700 text-right max-w-[60%]">
                {ticket.categoryName}
                {ticket.subCategoryName && (
                  <span className="text-gray-400"> / {ticket.subCategoryName}</span>
                )}
              </dd>
            </div>
            <div className="flex items-center justify-between">
              <dt className="text-gray-500">Assigned To</dt>
              <dd className="font-medium text-gray-700">
                {ticket.assignedAgentId ? (
                  <span className="flex items-center gap-1">
                    <Icon d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z" className="w-3.5 h-3.5 text-blue-500" />
                    {ticket.assignedAgentId}
                  </span>
                ) : (
                  <span className="text-gray-400 italic">Unassigned</span>
                )}
              </dd>
            </div>
            {ticket.orderId && (
              <div className="flex items-center justify-between">
                <dt className="text-gray-500">Order</dt>
                <dd className="font-mono text-xs font-medium text-gray-700">#{ticket.orderId}</dd>
              </div>
            )}

            {/* SLA countdowns */}
            {ticket.firstResponseDeadline && (
              <div className="pt-1 border-t border-gray-100">
                <SlaCountdown deadline={ticket.firstResponseDeadline} label="First Response" />
              </div>
            )}
            {ticket.resolutionDeadline && (
              <div>
                <SlaCountdown deadline={ticket.resolutionDeadline} label="Resolution" />
              </div>
            )}

            {/* Tags */}
            {ticket.tags.length > 0 && (
              <div className="pt-1 border-t border-gray-100">
                <dt className="text-gray-500 mb-1.5">Tags</dt>
                <dd className="flex flex-wrap gap-1.5">
                  {ticket.tags.map((tag) => (
                    <span
                      key={tag}
                      className="text-xs bg-blue-50 text-blue-700 border border-blue-100 rounded-full px-2 py-0.5"
                    >
                      {tag}
                    </span>
                  ))}
                </dd>
              </div>
            )}

            <div className="pt-1 border-t border-gray-100 space-y-1">
              <div className="flex items-center justify-between text-xs">
                <span className="text-gray-400">Created</span>
                <span className="text-gray-600">{formatDate(ticket.createdAt)}</span>
              </div>
              <div className="flex items-center justify-between text-xs">
                <span className="text-gray-400">Updated</span>
                <span className="text-gray-600">{formatDate(ticket.updatedAt)}</span>
              </div>
            </div>
          </dl>
        </Panel>

        {/* Customer */}
        <Panel title="Customer">
          {customerProfile ? (
            <dl className="space-y-2.5 text-sm">
              <div className="flex items-center gap-3 mb-2">
                <div className="w-9 h-9 rounded-full bg-blue-100 flex items-center justify-center flex-shrink-0">
                  <span className="text-blue-700 font-bold text-sm">
                    {customerProfile.name.charAt(0).toUpperCase()}
                  </span>
                </div>
                <div>
                  <p className="font-semibold text-gray-800">{customerProfile.name}</p>
                  <p className="text-xs text-gray-400">
                    Joined {formatDate(customerProfile.joinedAt)}
                  </p>
                </div>
              </div>
              <div className="flex items-center justify-between">
                <dt className="text-gray-500">Phone</dt>
                <dd className="font-mono text-xs text-gray-700">
                  {maskPhone(customerProfile.phone)}
                </dd>
              </div>
              <div className="flex items-center justify-between">
                <dt className="text-gray-500">Email</dt>
                <dd className="text-gray-700 text-xs truncate max-w-[60%]">
                  {customerProfile.email || <span className="text-gray-400">&mdash;</span>}
                </dd>
              </div>
              <div className="flex items-center justify-between">
                <dt className="text-gray-500">Total Tickets</dt>
                <dd>
                  <span className="bg-gray-100 text-gray-700 text-xs font-bold px-2 py-0.5 rounded-full border border-gray-200">
                    {customerProfile.totalTickets}
                  </span>
                </dd>
              </div>
              <div className="flex items-center justify-between">
                <dt className="text-gray-500">Open Tickets</dt>
                <dd>
                  <span
                    className={`text-xs font-bold px-2 py-0.5 rounded-full border ${
                      customerProfile.openTickets > 0
                        ? 'bg-orange-50 text-orange-700 border-orange-200'
                        : 'bg-gray-100 text-gray-600 border-gray-200'
                    }`}
                  >
                    {customerProfile.openTickets}
                  </span>
                </dd>
              </div>
              <div className="flex items-center justify-between">
                <dt className="text-gray-500">Language</dt>
                <dd>
                  <span className="text-xs bg-indigo-50 text-indigo-700 border border-indigo-100 rounded-full px-2 py-0.5">
                    {customerProfile.preferredLanguage}
                  </span>
                </dd>
              </div>
              {customerProfile.lastOrderAt && (
                <div className="flex items-center justify-between">
                  <dt className="text-gray-500">Last Order</dt>
                  <dd className="text-xs text-gray-600">{formatDate(customerProfile.lastOrderAt)}</dd>
                </div>
              )}
            </dl>
          ) : (
            <div className="space-y-2.5 text-sm">
              <div className="flex items-center justify-between">
                <span className="text-gray-500">Name</span>
                <span className="font-medium text-gray-800">{ticket.customerName}</span>
              </div>
              {ticket.customerPhone && (
                <div className="flex items-center justify-between">
                  <span className="text-gray-500">Phone</span>
                  <span className="font-mono text-xs text-gray-700">
                    {maskPhone(ticket.customerPhone)}
                  </span>
                </div>
              )}
              {ticket.customerEmail && (
                <div className="flex items-center justify-between">
                  <span className="text-gray-500">Email</span>
                  <span className="text-xs text-gray-700 truncate">{ticket.customerEmail}</span>
                </div>
              )}
            </div>
          )}
        </Panel>

        {/* AI Assistance */}
        <Panel title="AI Assistance">
          {ticket.sentimentLabel && (
            <div className="flex items-center gap-2 mb-3 pb-3 border-b border-gray-100">
              <span className="text-xs text-gray-500">Sentiment</span>
              <span
                className={`text-xs font-semibold px-2 py-0.5 rounded-full ${
                  SENTIMENT_COLOR[ticket.sentimentLabel] ?? 'bg-gray-100 text-gray-700'
                }`}
              >
                {ticket.sentimentLabel}
              </span>
            </div>
          )}

          {aiSuggestions.length === 0 ? (
            <p className="text-xs text-gray-400 italic text-center py-3">
              No suggestions available
            </p>
          ) : (
            <div className="space-y-3">
              {aiSuggestions.map((suggestion, idx) => (
                <div
                  key={idx}
                  className="rounded-lg border border-gray-200 bg-gray-50 p-3 hover:border-blue-200 hover:bg-blue-50 transition-colors"
                >
                  <div className="flex items-start justify-between gap-2 mb-1.5">
                    <p className="text-xs font-semibold text-gray-700 leading-snug">
                      {suggestion.title}
                    </p>
                    <button
                      type="button"
                      onClick={() => {
                        setReplyContent(suggestion.content);
                        setIsInternal(false);
                      }}
                      className="flex-shrink-0 text-xs text-blue-600 hover:text-blue-800 font-medium bg-white border border-blue-200 hover:border-blue-400 px-2 py-0.5 rounded transition-colors"
                    >
                      Use
                    </button>
                  </div>
                  <p className="text-xs text-gray-500 line-clamp-2 mb-2">{suggestion.content}</p>
                  <div className="flex items-center gap-2">
                    <div className="flex-1 h-1.5 bg-gray-200 rounded-full overflow-hidden">
                      <div
                        className="h-full bg-blue-500 rounded-full"
                        style={{ width: `${Math.round(suggestion.confidence * 100)}%` }}
                      />
                    </div>
                    <span className="text-xs text-gray-500 flex-shrink-0">
                      {Math.round(suggestion.confidence * 100)}%
                    </span>
                  </div>
                </div>
              ))}
            </div>
          )}
        </Panel>

        {/* Related Tickets */}
        <Panel title="Related Tickets">
          {relatedTickets.length === 0 ? (
            <p className="text-xs text-gray-400 italic text-center py-3">
              No related tickets
            </p>
          ) : (
            <div className="space-y-2">
              {relatedTickets.map((rt) => (
                <Link
                  key={rt.id}
                  to={`/tickets/${rt.ticketNumber}`}
                  className="block rounded-lg border border-gray-200 bg-white p-3 hover:border-blue-300 hover:bg-blue-50 transition-colors group"
                >
                  <div className="flex items-center gap-1.5 mb-1">
                    <span className="font-mono text-xs text-gray-400">#{rt.ticketNumber}</span>
                    <span
                      className={`text-xs font-semibold px-1.5 py-0.5 rounded-full ${
                        STATUS_COLOR[rt.status] ?? 'bg-gray-100 text-gray-700'
                      }`}
                    >
                      {rt.status.replace('_', ' ')}
                    </span>
                    {rt.slaBreached && (
                      <span className="text-xs text-red-600 font-bold">SLA</span>
                    )}
                  </div>
                  <p className="text-xs font-medium text-gray-700 group-hover:text-blue-700 line-clamp-1">
                    {rt.title}
                  </p>
                  <div className="flex items-center gap-2 mt-1">
                    <span
                      className={`text-xs px-1.5 py-0.5 rounded ${
                        PRIORITY_COLOR[rt.priority] ?? 'bg-gray-100 text-gray-600'
                      }`}
                    >
                      {rt.priority}
                    </span>
                    <span className="text-xs text-gray-400">{timeAgo(rt.updatedAt)}</span>
                  </div>
                </Link>
              ))}
            </div>
          )}
        </Panel>
      </div>
    </div>
  );
}
