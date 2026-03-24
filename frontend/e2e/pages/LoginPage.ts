import type { Page } from '@playwright/test';
import { expect } from '@playwright/test';

/**
 * Page Object Model for the customer OTP login page (customer-portal).
 * Maps to: /login on http://localhost:3000
 *
 * Actual UI (LoginPage.tsx):
 *   - Step 1: "Phone Number" label input + "Send OTP" button
 *   - Step 2: "One-Time Password" label input + "Verify OTP" button
 *   - Error messages rendered as role="alert"
 */
export class LoginPage {
  constructor(private readonly page: Page) {}

  /** Fill in the phone number input. */
  async enterPhone(phone: string): Promise<void> {
    await this.page.getByLabel('Phone Number').fill(phone);
  }

  /** Click the "Send OTP" button to submit the phone form. */
  async submitPhone(): Promise<void> {
    await this.page.getByRole('button', { name: 'Send OTP' }).click();
  }

  /** Fill in the OTP input. */
  async enterOtp(otp: string): Promise<void> {
    await this.page.getByLabel('One-Time Password').fill(otp);
  }

  /** Click the "Verify OTP" button to submit the OTP form. */
  async submitOtp(): Promise<void> {
    await this.page.getByRole('button', { name: 'Verify OTP' }).click();
  }

  /**
   * Assert the user is logged in.
   * After successful OTP verification the app navigates to /tickets.
   */
  async expectLoggedIn(): Promise<void> {
    await expect(this.page).toHaveURL(/\/(tickets|dashboard)/);
  }
}
