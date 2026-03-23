import { useParams, Link } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useForm, type SubmitHandler } from 'react-hook-form';
import { Badge, Card, CardContent, CardHeader, CardTitle, Spinner, TicketStatusBadge } from '@supporthub/ui';
import type { Priority, TicketActivity } from '@supporthub/customer-sdk';
import { useAuthStore } from '../store/authStore.js';
import { getApiClient } from '../api/client.js';

interface CommentFormValues {
  comment: string;
}

interface AiSuggestion {
  suggestion: string;
  confidence: number;
}

interface AiSuggestionsResponse {
  suggestions: AiSuggestion[];
}

const PRIORITY_VARIANT: Record<Priority, 'default' | 'destructive' | 'secondary' | 'outline'> = {
  LOW: 'secondary',
  MEDIUM: 'outline',
  HIGH: 'default',
  URGENT: 'destructive',
};

function formatDate(iso: string): string {
  return new Date(iso).toLocaleString('en-IN', {
    day: '2-digit',
    month: 'short',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
}

function ActivityItem({ activity }: { activity: TicketActivity }) {
  const isCustomer = activity.actorType === 'CUSTOMER';

  if (activity.activityType === 'COMMENT') {
    return (
      <div className={`flex ${isCustomer ? 'justify-end' : 'justify-start'}`}>
        <div
          className={`max-w-prose rounded-2xl px-4 py-3 text-sm ${
            isCustomer
              ? 'rounded-br-sm bg-blue-600 text-white'
              : 'rounded-bl-sm bg-gray-100 text-gray-900'
          }`}
        >
          <p className="whitespace-pre-wrap">{activity.content}</p>
          <p
            className={`mt-1 text-xs ${isCustomer ? 'text-blue-100' : 'text-gray-500'}`}
          >
            {isCustomer ? 'You' : 'Support Agent'} · {formatDate(activity.createdAt)}
          </p>
        </div>
      </div>
    );
  }

  return (
    <div className="flex justify-center">
      <span className="rounded-full bg-gray-100 px-3 py-1 text-xs text-gray-500">
        {activity.content} · {formatDate(activity.createdAt)}
      </span>
    </div>
  );
}

export function TicketDetailPage() {
  const { id } = useParams<{ id: string }>();
  const tenantId = useAuthStore((s) => s.tenantId) ?? '';
  const client = getApiClient(tenantId);
  const queryClient = useQueryClient();

  const ticketQuery = useQuery({
    queryKey: ['ticket', id],
    queryFn: () => {
      if (id === undefined) throw new Error('Ticket ID is required');
      return client.getTicket(id);
    },
    enabled: id !== undefined,
  });

  const activitiesQuery = useQuery({
    queryKey: ['activities', id],
    queryFn: () => {
      if (id === undefined) throw new Error('Ticket ID is required');
      return client.getTicketActivities(id);
    },
    enabled: id !== undefined,
  });

  const aiSuggestionsQuery = useQuery({
    queryKey: ['ai-suggestions', id],
    queryFn: async (): Promise<AiSuggestionsResponse> => {
      const token = useAuthStore.getState().token ?? '';
      const apiBase = import.meta.env.VITE_API_BASE_URL as string;
      const response = await fetch(`${apiBase}/api/v1/ai/resolution-suggestions/${id ?? ''}`, {
        headers: {
          Authorization: `Bearer ${token}`,
          'X-Tenant-ID': tenantId,
        },
      });
      if (!response.ok) {
        throw new Error(`HTTP ${response.status}`);
      }
      return response.json() as Promise<AiSuggestionsResponse>;
    },
    enabled: id !== undefined,
  });

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isSubmitting },
  } = useForm<CommentFormValues>();

  const commentMutation = useMutation({
    mutationFn: (content: string) => {
      if (id === undefined) throw new Error('Ticket ID is required');
      return client.addComment(id, content);
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['activities', id] });
      reset();
    },
  });

  const onSubmitComment: SubmitHandler<CommentFormValues> = (data) => {
    commentMutation.mutate(data.comment);
  };

  if (id === undefined) {
    return (
      <Card>
        <CardContent className="py-8 text-center">
          <p className="text-red-600">Invalid ticket ID.</p>
        </CardContent>
      </Card>
    );
  }

  if (ticketQuery.isLoading) {
    return (
      <div className="flex justify-center py-12">
        <Spinner size="lg" label="Loading ticket..." />
      </div>
    );
  }

  if (ticketQuery.isError || ticketQuery.data === undefined) {
    return (
      <Card>
        <CardContent className="py-8 text-center">
          <p className="text-red-600">
            {ticketQuery.error instanceof Error
              ? ticketQuery.error.message
              : 'Failed to load ticket.'}
          </p>
          <Link to="/tickets" className="mt-4 inline-block text-sm text-blue-600 hover:underline">
            Back to My Tickets
          </Link>
        </CardContent>
      </Card>
    );
  }

  const ticket = ticketQuery.data;
  const activities = activitiesQuery.data ?? [];
  const aiSuggestions = aiSuggestionsQuery.data?.suggestions ?? [];

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center gap-2">
        <Link to="/tickets" className="text-sm text-blue-600 hover:underline">
          My Tickets
        </Link>
        <span className="text-gray-400">/</span>
        <span className="text-sm text-gray-600 font-mono">{ticket.ticketNumber}</span>
      </div>

      <Card>
        <CardHeader>
          <div className="flex items-start justify-between gap-4">
            <CardTitle className="text-xl">{ticket.title}</CardTitle>
            <div className="flex shrink-0 gap-2">
              <TicketStatusBadge status={ticket.status} />
              <Badge variant={PRIORITY_VARIANT[ticket.priority]}>{ticket.priority}</Badge>
            </div>
          </div>
        </CardHeader>
        <CardContent className="flex flex-col gap-3">
          <p className="text-sm text-gray-700 whitespace-pre-wrap">{ticket.description}</p>
          <div className="flex flex-wrap gap-4 border-t border-gray-100 pt-3 text-xs text-gray-500">
            <span>Ticket: <span className="font-mono text-gray-700">{ticket.ticketNumber}</span></span>
            <span>Created: {formatDate(ticket.createdAt)}</span>
            <span>Updated: {formatDate(ticket.updatedAt)}</span>
            {ticket.slaBreached && (
              <Badge variant="destructive">SLA Breached</Badge>
            )}
          </div>
        </CardContent>
      </Card>

      {aiSuggestions.length > 0 && (
        <Card>
          <CardHeader>
            <CardTitle className="text-base">AI Resolution Suggestions</CardTitle>
          </CardHeader>
          <CardContent className="flex flex-col gap-3">
            {aiSuggestions.map((s, idx) => (
              <div key={idx} className="rounded-lg bg-blue-50 p-3">
                <p className="text-sm text-gray-800">{s.suggestion}</p>
                <div className="mt-2 flex items-center gap-2">
                  <div className="h-1.5 flex-1 rounded-full bg-gray-200">
                    <div
                      className="h-1.5 rounded-full bg-blue-500"
                      style={{ width: `${Math.round(s.confidence * 100)}%` }}
                    />
                  </div>
                  <span className="text-xs text-gray-500">
                    {Math.round(s.confidence * 100)}% confidence
                  </span>
                </div>
              </div>
            ))}
          </CardContent>
        </Card>
      )}

      <Card>
        <CardHeader>
          <CardTitle className="text-base">Conversation</CardTitle>
        </CardHeader>
        <CardContent className="flex flex-col gap-4">
          {activitiesQuery.isLoading && (
            <div className="flex justify-center py-4">
              <Spinner size="md" label="Loading conversation..." />
            </div>
          )}

          {activitiesQuery.isError && (
            <p className="text-sm text-red-600">Failed to load conversation history.</p>
          )}

          {!activitiesQuery.isLoading && !activitiesQuery.isError && activities.length === 0 && (
            <p className="text-center text-sm text-gray-500">No messages yet.</p>
          )}

          {activities.length > 0 && (
            <div className="flex flex-col gap-3">
              {activities.map((activity) => (
                <ActivityItem key={activity.id} activity={activity} />
              ))}
            </div>
          )}

          {commentMutation.isError && (
            <p className="text-sm text-red-600" role="alert">
              {commentMutation.error instanceof Error
                ? commentMutation.error.message
                : 'Failed to send comment. Please try again.'}
            </p>
          )}

          <form
            onSubmit={(e) => {
              void handleSubmit(onSubmitComment)(e);
            }}
            className="flex flex-col gap-2 border-t border-gray-100 pt-4"
            noValidate
          >
            <div className="flex flex-col gap-1">
              <label htmlFor="comment" className="text-sm font-medium text-gray-700">
                Add a comment
              </label>
              <textarea
                id="comment"
                rows={3}
                placeholder="Type your message..."
                className="flex w-full rounded-md border border-gray-300 bg-white px-3 py-2 text-sm placeholder:text-gray-400 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-600 focus-visible:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50"
                aria-invalid={errors.comment !== undefined}
                {...register('comment', {
                  required: 'Comment cannot be empty',
                  minLength: { value: 1, message: 'Comment cannot be empty' },
                  maxLength: { value: 2000, message: 'Comment must be at most 2000 characters' },
                })}
              />
              {errors.comment !== undefined && (
                <p className="text-xs text-red-600" role="alert">
                  {errors.comment.message}
                </p>
              )}
            </div>
            <div className="flex justify-end">
              <button
                type="submit"
                disabled={isSubmitting || commentMutation.isPending}
                className="rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700 disabled:cursor-not-allowed disabled:opacity-50"
              >
                {commentMutation.isPending ? (
                  <Spinner size="sm" label="Sending..." />
                ) : (
                  'Send'
                )}
              </button>
            </div>
          </form>
        </CardContent>
      </Card>
    </div>
  );
}
