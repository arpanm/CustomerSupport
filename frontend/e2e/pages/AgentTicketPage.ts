import type { Page } from '@playwright/test';
import { expect } from '@playwright/test';

/**
 * Page Object Model for the agent ticket detail page (agent-dashboard).
 * Maps to: /tickets/{ticketNumber} on http://localhost:3001
 *
 * Actual UI (agent-dashboard/src/pages/TicketDetailPage.tsx):
 *   - "Resolve" button (green, visible when ticket is OPEN/IN_PROGRESS/PENDING_CUSTOMER_RESPONSE)
 *   - AI suggestions rendered in AiAssistancePanel as buttons; clicking fills the reply textarea
 *   - Ticket status shown via <TicketStatusBadge> component
 *   - Confirmation happens inline (button click triggers status mutation directly, no modal)
 */
export class AgentTicketPage {
  constructor(private readonly page: Page) {}

  /**
   * Click the "Resolve" button to mark the ticket as resolved.
   * The Resolve button triggers the status mutation directly (no confirmation dialog).
   */
  async resolveTicket(): Promise<void> {
    await this.page.getByRole('button', { name: 'Resolve' }).click();
  }

  /**
   * Click the first AI suggestion button to apply it as a reply.
   * In AiAssistancePanel, each suggestion is rendered as a <button> with the suggestion title.
   * Clicking applies the suggestion content to the reply textarea.
   */
  async applyAISuggestion(): Promise<void> {
    // Suggestions are inside the AI Assistance card; each is a plain button
    const panel = this.page.getByRole('heading', { name: /AI Assistance/i }).locator('../..');
    await panel.getByRole('button').first().click();
  }

  /**
   * Assert the ticket status badge shows the expected status value.
   * @param status - Expected status string, e.g. 'RESOLVED', 'OPEN'.
   */
  async expectStatus(status: string): Promise<void> {
    await expect(
      this.page.getByText(status, { exact: true }).first(),
    ).toBeVisible({ timeout: 10_000 });
  }
}
