import { test, expect } from '@playwright/test';
import { LoginPage } from '../pages/LoginPage';

test.describe('OTP Login', () => {
  test('customer can log in with phone and OTP', async ({ page }) => {
    const loginPage = new LoginPage(page);
    await page.goto('/login');
    await loginPage.enterPhone('+919876543210');
    await loginPage.submitPhone();
    // In test env, OTP is fixed to 123456
    await loginPage.enterOtp('123456');
    await loginPage.submitOtp();
    await loginPage.expectLoggedIn();
  });

  test('invalid OTP shows error', async ({ page }) => {
    const loginPage = new LoginPage(page);
    await page.goto('/login');
    await loginPage.enterPhone('+919876543210');
    await loginPage.submitPhone();
    await loginPage.enterOtp('000000');
    await loginPage.submitOtp();
    await expect(page.getByRole('alert')).toBeVisible();
  });
});
