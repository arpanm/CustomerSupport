import type { Page } from '@playwright/test';
import { expect } from '@playwright/test';

/**
 * Page Object Model for the customer ticket detail page (customer-portal).
 * Maps to: /tickets/{ticketNumber} on http://localhost:3000
 *
 * Actual UI (TicketDetailPage.tsx):
 *   - "Add a comment" label → <textarea id="comment">
 *   - "Send" button submits the comment form
 *   - Activity messages rendered inside the conversation card
 *   - "AI Resolution Suggestions" card title present when suggestions exist
 *   - Ticket status displayed via <TicketStatusBadge> (text inside the badge)
 */
export class TicketDetailPage {
  constructor(private readonly page: Page) {}

  /**
   * Fill the comment textarea and submit.
   * @param text - Comment text to add.
   */
  async addComment(text: string): Promise<void> {
    await this.page.getByLabel('Add a comment').fill(text);
    await this.page.getByRole('button', { name: 'Send' }).click();
  }

  /**
   * Assert that the given comment text appears in the activity feed.
   * @param text - The comment text to look for.
   */
  async expectComment(text: string): Promise<void> {
    await expect(this.page.getByText(text)).toBeVisible({ timeout: 10_000 });
  }

  /**
   * Assert that the AI Resolution Suggestions panel is visible.
   * The panel heading is "AI Resolution Suggestions".
   */
  async expectAISuggestions(): Promise<void> {
    await expect(
      this.page.getByRole('heading', { name: 'AI Resolution Suggestions' }),
    ).toBeVisible({ timeout: 15_000 });
  }

  /**
   * Return the current ticket status text from the status badge.
   * The TicketStatusBadge renders the status as text inside the badge element.
   */
  async getStatus(): Promise<string> {
    // The badge is rendered alongside the ticket title in the header card
    const badge = this.page.locator('[data-testid="ticket-status-badge"]').first();
    const fallback = this.page.getByText(/OPEN|IN_PROGRESS|RESOLVED|CLOSED|ESCALATED|PENDING/i).first();
    const target = (await badge.count()) > 0 ? badge : fallback;
    const text = await target.textContent();
    return text?.trim() ?? '';
  }
}
