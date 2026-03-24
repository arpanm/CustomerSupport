import type { Page } from '@playwright/test';
import { expect } from '@playwright/test';

/**
 * Page Object Model for the FAQ search page (customer-portal).
 * Maps to: /faqs on http://localhost:3000
 *
 * Actual UI (FAQSearchPage.tsx):
 *   - Search input has aria-label="Search FAQs"
 *   - FAQ results rendered as Card components with question as CardTitle
 *   - Empty state: "No FAQs found. Try different keywords or create a support ticket."
 *   - Answer content is visible within each FAQ Card
 *   - Search is debounced (400 ms); query must be > 2 chars to trigger API call
 */
export class FAQSearchPage {
  constructor(private readonly page: Page) {}

  /**
   * Fill the search input and wait for the debounce to fire.
   * The component debounces input by 400 ms and only queries when length > 2.
   * @param query - The search term to enter.
   */
  async search(query: string): Promise<void> {
    await this.page.getByLabel('Search FAQs').fill(query);
    // Wait for debounce + network request
    await this.page.waitForTimeout(500);
  }

  /**
   * Assert that at least one FAQ result card is visible.
   */
  async expectResults(): Promise<void> {
    await expect(
      this.page.getByRole('article').first(),
    ).toBeVisible({ timeout: 10_000 });
  }

  /**
   * Assert that the empty-state message is visible (no results found).
   */
  async expectNoResults(): Promise<void> {
    await expect(
      this.page.getByText('No FAQs found.', { exact: false }),
    ).toBeVisible({ timeout: 10_000 });
  }

  /**
   * Click the first FAQ result in the list.
   * Each result is rendered as a Card; clicking it should show the full answer.
   */
  async clickFirstResult(): Promise<void> {
    await this.page.getByRole('article').first().click();
  }

  /**
   * Assert that the FAQ answer content area is visible.
   * After clicking a result the full answer text becomes visible.
   */
  async expectAnswerVisible(): Promise<void> {
    // The answer excerpt is rendered inside CardContent as a <p> element
    await expect(
      this.page.getByRole('article').first().locator('p').first(),
    ).toBeVisible({ timeout: 10_000 });
  }
}
