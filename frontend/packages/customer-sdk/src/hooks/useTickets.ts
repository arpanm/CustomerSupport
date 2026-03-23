import { useState, useCallback } from 'react';
import type { SupportHubClient } from '../client.js';
import type { Ticket, CreateTicketRequest, PaginatedResponse } from '../types/index.js';

export function useTickets(client: SupportHubClient) {
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const createTicket = useCallback(async (req: CreateTicketRequest): Promise<Ticket | null> => {
    setLoading(true);
    setError(null);
    try {
      return await client.createTicket(req);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to create ticket');
      return null;
    } finally {
      setLoading(false);
    }
  }, [client]);

  const listMyTickets = useCallback(async (cursor?: string): Promise<PaginatedResponse<Ticket> | null> => {
    setLoading(true);
    setError(null);
    try {
      return await client.listMyTickets(cursor);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to load tickets');
      return null;
    } finally {
      setLoading(false);
    }
  }, [client]);

  return { createTicket, listMyTickets, loading, error };
}
