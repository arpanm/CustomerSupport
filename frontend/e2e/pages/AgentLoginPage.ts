import type { Page } from '@playwright/test';
import { expect } from '@playwright/test';

/**
 * Page Object Model for the agent login page (agent-dashboard).
 * Maps to: /login on http://localhost:3001
 *
 * Actual UI (agent-dashboard/src/pages/LoginPage.tsx):
 *   - "Email" label input (type=email)
 *   - "Password" label input (type=password)
 *   - "Sign In" button (type=submit)
 *   - After successful login, navigates to "/" which shows the TicketQueuePage
 *   - Error shown via role="alert" div when credentials are wrong
 */
export class AgentLoginPage {
  constructor(private readonly page: Page) {}

  /**
   * Fill in the email and password fields and submit the form.
   * @param email - Agent email address.
   * @param password - Agent password (min 8 chars as per validation).
   */
  async login(email: string, password: string): Promise<void> {
    await this.page.getByLabel('Email').fill(email);
    await this.page.getByLabel('Password').fill(password);
    await this.page.getByRole('button', { name: 'Sign In' }).click();
  }

  /**
   * Assert that the ticket queue is visible after login.
   * The TicketQueuePage renders a heading "Ticket Queue".
   */
  async expectQueue(): Promise<void> {
    await expect(
      this.page.getByRole('heading', { name: 'Ticket Queue' }),
    ).toBeVisible({ timeout: 10_000 });
  }
}
