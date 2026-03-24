import type { Page } from '@playwright/test';
import { expect } from '@playwright/test';

/**
 * Page Object Model for the customer ticket list page (customer-portal).
 * Maps to: /tickets on http://localhost:3000
 *
 * Actual UI (TicketListPage.tsx):
 *   - Status filter buttons (role="button") for All / Open / In Progress / Resolved
 *   - Table rows with ticket data (each row has ticket title in a <td>)
 *   - Clicking a row navigates to /tickets/{ticketNumber}
 */
export class TicketListPage {
  constructor(private readonly page: Page) {}

  /** Wait until the tickets table or the empty-state message is visible. */
  async waitForTickets(): Promise<void> {
    await expect(
      this.page.getByRole('table').or(this.page.getByText('No tickets found.')),
    ).toBeVisible({ timeout: 10_000 });
  }

  /**
   * Click one of the status filter buttons.
   * @param status - One of: 'All', 'Open', 'In Progress', 'Resolved'
   */
  async filterByStatus(status: string): Promise<void> {
    await this.page.getByRole('button', { name: status, exact: true }).click();
  }

  /**
   * Click the nth ticket row (0-based index).
   * Each row in the tickets table is a clickable <tr>.
   */
  async clickTicket(index: number): Promise<void> {
    await this.page.getByRole('row').nth(index + 1).click(); // +1 skips the header row
  }

  /**
   * Assert that at least `min` ticket rows are visible in the table body.
   */
  async expectTicketCount(min: number): Promise<void> {
    const rows = this.page.getByRole('row').filter({ has: this.page.getByRole('cell') });
    await expect(rows).toHaveCount(expect.greaterThanOrEqual(min) as never);
    // Use a more straightforward approach
    const count = await rows.count();
    expect(count).toBeGreaterThanOrEqual(min);
  }

  /**
   * Return the visible ticket title texts from the Subject column (2nd <td>).
   */
  async getTicketTitles(): Promise<string[]> {
    const titleCells = this.page.getByRole('cell').filter({ hasText: /\S+/ });
    // The subject is the 2nd column; collect all row texts from the body
    const rows = this.page.locator('tbody tr');
    const count = await rows.count();
    const titles: string[] = [];
    for (let i = 0; i < count; i++) {
      const cells = rows.nth(i).getByRole('cell');
      const subjectText = await cells.nth(1).textContent();
      if (subjectText !== null) {
        titles.push(subjectText.trim());
      }
    }
    return titles;
  }
}
