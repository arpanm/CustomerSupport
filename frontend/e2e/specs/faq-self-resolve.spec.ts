import { test, expect } from '@playwright/test';
import { FAQSearchPage } from '../pages/FAQSearchPage';

test.describe('FAQ Self-Resolve', () => {
  test('customer finds FAQ answer and deflects ticket creation', async ({ page }) => {
    await page.goto('/faqs');
    const faqPage = new FAQSearchPage(page);
    await faqPage.search('refund');
    await faqPage.expectResults();
    await faqPage.clickFirstResult();
    await faqPage.expectAnswerVisible();
  });

  test('no results shown for unknown query', async ({ page }) => {
    await page.goto('/faqs');
    const faqPage = new FAQSearchPage(page);
    await faqPage.search('xyzunknownquery12345');
    await faqPage.expectNoResults();
  });
});
