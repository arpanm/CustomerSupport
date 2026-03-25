import { test, expect } from '@playwright/test';
import { LoginPage } from '../pages/LoginPage';
import { TicketListPage } from '../pages/TicketListPage';

test.describe('Ticket Creation', () => {
  test.beforeEach(async ({ page }) => {
    // Login first
    const loginPage = new LoginPage(page);
    await page.goto('/login');
    await loginPage.enterPhone('+919876543210');
    await loginPage.submitPhone();
    await loginPage.enterOtp('123456');
    await loginPage.submitOtp();
  });

  test('customer can create a new ticket', async ({ page }) => {
    await page.goto('/tickets/create');
    await page.getByLabel('Subject').fill('Test ticket subject');
    await page.getByLabel('Description').fill('This is a test description for the ticket');
    await page.getByRole('button', { name: 'Submit' }).click();
    await expect(page).toHaveURL(/\/tickets/);
    const listPage = new TicketListPage(page);
    await listPage.waitForTickets();
    await listPage.expectTicketCount(1);
  });

  test('form validation prevents empty submission', async ({ page }) => {
    await page.goto('/tickets/create');
    await page.getByRole('button', { name: 'Submit' }).click();
    await expect(page.getByText(/required/i)).toBeVisible();
  });
});
