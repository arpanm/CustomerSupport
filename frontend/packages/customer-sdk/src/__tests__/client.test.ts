import { describe, it, expect, vi, beforeEach } from 'vitest';
import { SupportHubClient, SupportHubError } from '../index.js';

describe('SupportHubClient', () => {
  let client: SupportHubClient;

  beforeEach(() => {
    client = new SupportHubClient({
      baseUrl: 'http://localhost:8080',
      tenantId: 'test-tenant',
      getAccessToken: () => 'test-token',
    });
    globalThis.fetch = vi.fn();
  });

  it('should send correct headers on createTicket', async () => {
    (globalThis.fetch as ReturnType<typeof vi.fn>).mockResolvedValue({
      ok: true,
      json: () => Promise.resolve({ data: { id: '1', ticketNumber: 'FC-2024-001' }, meta: {} }),
    });

    await client.createTicket({ title: 'Test', description: 'Test desc', categoryId: 'cat-1' });

    expect(globalThis.fetch).toHaveBeenCalledWith(
      'http://localhost:8080/api/v1/tickets',
      // eslint-disable-next-line @typescript-eslint/no-unsafe-assignment
      expect.objectContaining({
        // eslint-disable-next-line @typescript-eslint/no-unsafe-assignment
        headers: expect.objectContaining({
          'X-Tenant-ID': 'test-tenant',
          'Authorization': 'Bearer test-token',
        }),
      })
    );
  });

  it('should throw SupportHubError on API error', async () => {
    (globalThis.fetch as ReturnType<typeof vi.fn>).mockResolvedValue({
      ok: false,
      status: 404,
      json: () => Promise.resolve({ error: { code: 'NOT_FOUND', message: 'Ticket not found' }, meta: {} }),
    });

    await expect(client.getTicket('FC-2024-001')).rejects.toThrow(SupportHubError);
  });

  it('should handle token as function', async () => {
    const tokenFn = vi.fn().mockResolvedValue('dynamic-token');
    const dynamicClient = new SupportHubClient({
      baseUrl: 'http://localhost:8080',
      tenantId: 'test-tenant',
      getAccessToken: tokenFn,
    });

    (globalThis.fetch as ReturnType<typeof vi.fn>).mockResolvedValue({
      ok: true,
      json: () => Promise.resolve({ data: [], meta: {} }),
    });

    await dynamicClient.listMyTickets();
    expect(tokenFn).toHaveBeenCalled();
  });
});
