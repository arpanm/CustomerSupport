import { test, expect } from '@playwright/test';
import { AgentLoginPage } from '../pages/AgentLoginPage';
import { AgentTicketPage } from '../pages/AgentTicketPage';

test.describe('Agent Ticket Resolution', () => {
  test('agent can log in and resolve a ticket', async ({ page }) => {
    const agentLogin = new AgentLoginPage(page);
    await page.goto('http://localhost:3001/login');
    await agentLogin.login('agent@example.com', 'password123');
    await agentLogin.expectQueue();
    // Click first ticket in queue
    await page.getByTestId('ticket-queue-item').first().click();
    const ticketPage = new AgentTicketPage(page);
    await ticketPage.resolveTicket();
    await ticketPage.expectStatus('RESOLVED');
  });
});
