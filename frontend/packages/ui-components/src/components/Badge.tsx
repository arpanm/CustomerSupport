import { cva, type VariantProps } from 'class-variance-authority';
import { type HTMLAttributes } from 'react';
import { cn } from '../utils.js';

const badgeVariants = cva(
  'inline-flex items-center rounded-full border px-2.5 py-0.5 text-xs font-semibold transition-colors focus:outline-none focus:ring-2 focus:ring-offset-2',
  {
    variants: {
      variant: {
        default: 'border-transparent bg-blue-600 text-white',
        secondary: 'border-transparent bg-gray-100 text-gray-900',
        destructive: 'border-transparent bg-red-600 text-white',
        outline: 'text-gray-900 border-gray-300',
        open: 'border-transparent bg-blue-100 text-blue-800',
        in_progress: 'border-transparent bg-yellow-100 text-yellow-800',
        waiting_for_customer: 'border-transparent bg-orange-100 text-orange-800',
        resolved: 'border-transparent bg-green-100 text-green-800',
        closed: 'border-transparent bg-gray-100 text-gray-600',
      },
    },
    defaultVariants: {
      variant: 'default',
    },
  },
);

export type TicketStatusVariant =
  | 'open'
  | 'in_progress'
  | 'waiting_for_customer'
  | 'resolved'
  | 'closed';

export interface BadgeProps
  extends HTMLAttributes<HTMLDivElement>,
    VariantProps<typeof badgeVariants> {}

export function Badge({ className, variant, ...props }: BadgeProps) {
  return <div className={cn(badgeVariants({ variant }), className)} {...props} />;
}

const TICKET_STATUS_LABELS: Record<string, string> = {
  OPEN: 'Open',
  IN_PROGRESS: 'In Progress',
  WAITING_FOR_CUSTOMER: 'Waiting',
  RESOLVED: 'Resolved',
  CLOSED: 'Closed',
};

export interface TicketStatusBadgeProps extends HTMLAttributes<HTMLDivElement> {
  status: string;
}

export function TicketStatusBadge({ status, className, ...props }: TicketStatusBadgeProps) {
  const variant = status.toLowerCase() as TicketStatusVariant;
  const label = TICKET_STATUS_LABELS[status] ?? status;

  return (
    <Badge variant={variant} className={className} {...props}>
      {label}
    </Badge>
  );
}
