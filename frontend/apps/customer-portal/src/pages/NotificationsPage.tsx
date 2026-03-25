import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { Badge, Card, CardContent, Spinner } from '@supporthub/ui';
import type { Notification } from '@supporthub/customer-sdk';
import { useAuthStore } from '../store/authStore.js';
import { getApiClient } from '../api/client.js';

type ChannelVariant = 'default' | 'secondary' | 'outline';
type StatusVariant = 'default' | 'secondary' | 'destructive' | 'outline';

const CHANNEL_VARIANT: Record<Notification['channel'], ChannelVariant> = {
  IN_APP: 'default',
  SMS: 'secondary',
  EMAIL: 'outline',
  WHATSAPP: 'secondary',
};

const STATUS_VARIANT: Record<Notification['status'], StatusVariant> = {
  PENDING: 'outline',
  SENT: 'secondary',
  DELIVERED: 'default',
  FAILED: 'destructive',
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

interface NotificationItemProps {
  notification: Notification;
  onMarkRead: (id: string) => void;
  isMarkingRead: boolean;
}

function NotificationItem({ notification, onMarkRead, isMarkingRead }: NotificationItemProps) {
  return (
    <div className="flex items-start justify-between gap-4 rounded-lg border border-gray-200 bg-white p-4 shadow-sm">
      <div className="flex flex-1 flex-col gap-1.5">
        {notification.subject && (
          <p className="text-sm font-semibold text-gray-900">{notification.subject}</p>
        )}
        <p className="text-sm text-gray-700">{notification.content}</p>
        <div className="flex flex-wrap items-center gap-2 pt-1">
          <Badge variant={CHANNEL_VARIANT[notification.channel]}>
            {notification.channel}
          </Badge>
          <Badge variant={STATUS_VARIANT[notification.status]}>
            {notification.status}
          </Badge>
          <span className="text-xs text-gray-400">{formatDate(notification.createdAt)}</span>
        </div>
      </div>
      {notification.status !== 'DELIVERED' && (
        <button
          type="button"
          onClick={() => onMarkRead(notification.id)}
          disabled={isMarkingRead}
          className="shrink-0 rounded-md border border-gray-300 px-3 py-1.5 text-xs font-medium text-gray-700 hover:bg-gray-50 disabled:cursor-not-allowed disabled:opacity-50"
        >
          {isMarkingRead ? 'Marking...' : 'Mark read'}
        </button>
      )}
    </div>
  );
}

export function NotificationsPage() {
  const tenantId = useAuthStore((s) => s.tenantId) ?? '';
  const client = getApiClient(tenantId);
  const queryClient = useQueryClient();

  const { data, isLoading, isError, error } = useQuery({
    queryKey: ['notifications'],
    queryFn: () => client.getNotifications(),
  });

  const markReadMutation = useMutation({
    mutationFn: (notificationId: string) => client.markNotificationRead(notificationId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['notifications'] });
      void queryClient.invalidateQueries({ queryKey: ['unreadCount'] });
    },
  });

  const notifications = data?.data ?? [];
  const unreadCount = notifications.filter((n) => n.status !== 'DELIVERED').length;

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-2xl font-bold text-gray-900">Notifications</h2>
          <p className="mt-1 text-sm text-gray-600">
            Your recent notifications and messages.
          </p>
        </div>
        {unreadCount > 0 && (
          <span className="inline-flex items-center rounded-full bg-blue-600 px-2.5 py-0.5 text-xs font-semibold text-white">
            {unreadCount} unread
          </span>
        )}
      </div>

      {isLoading && (
        <div className="flex justify-center py-12">
          <Spinner size="lg" label="Loading notifications..." />
        </div>
      )}

      {isError && (
        <Card>
          <CardContent className="py-8 text-center">
            <p className="text-red-600">
              {error instanceof Error ? error.message : 'Failed to load notifications.'}
            </p>
          </CardContent>
        </Card>
      )}

      {!isLoading && !isError && notifications.length === 0 && (
        <Card>
          <CardContent className="py-10 text-center">
            <p className="text-gray-500">You have no notifications yet.</p>
          </CardContent>
        </Card>
      )}

      {markReadMutation.isError && (
        <p className="text-sm text-red-600" role="alert">
          {markReadMutation.error instanceof Error
            ? markReadMutation.error.message
            : 'Failed to mark notification as read.'}
        </p>
      )}

      {!isLoading && !isError && notifications.length > 0 && (
        <div className="flex flex-col gap-3">
          {notifications.map((notification) => (
            <NotificationItem
              key={notification.id}
              notification={notification}
              onMarkRead={(id) => markReadMutation.mutate(id)}
              isMarkingRead={markReadMutation.isPending}
            />
          ))}
        </div>
      )}
    </div>
  );
}
