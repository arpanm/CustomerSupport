import { create } from 'zustand';

export type TicketStatus =
  | 'OPEN'
  | 'PENDING_AGENT_RESPONSE'
  | 'PENDING_CUSTOMER_RESPONSE'
  | 'IN_PROGRESS'
  | 'ESCALATED'
  | 'RESOLVED'
  | 'CLOSED'
  | 'REOPENED';

export type Priority = 'LOW' | 'MEDIUM' | 'HIGH' | 'URGENT';

export interface TicketFilter {
  status: TicketStatus | '';
  priority: Priority | '';
  assignedToMe: boolean;
  search: string;
}

interface TicketFilterState {
  filter: TicketFilter;
  setFilter: (partial: Partial<TicketFilter>) => void;
  resetFilter: () => void;
}

const DEFAULT_FILTER: TicketFilter = {
  status: '',
  priority: '',
  assignedToMe: false,
  search: '',
};

export const useTicketFilterStore = create<TicketFilterState>()((set) => ({
  filter: DEFAULT_FILTER,
  setFilter: (partial) => set((s) => ({ filter: { ...s.filter, ...partial } })),
  resetFilter: () => set({ filter: DEFAULT_FILTER }),
}));
