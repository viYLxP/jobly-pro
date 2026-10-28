import { expect, test } from '@playwright/test';

import { createAccount, publishJob, signInOk } from './helpers';

const ADMIN = {
  email: 'admin@jobly.example',
  password: process.env.ADMIN_PASSWORD ?? 'Admin@12345',
};

test.describe('Arabic, right-to-left and small screens', () => {
  test('switching to Arabic flips the page direction and keeps what was typed', async ({
    page,
  }) => {
    await page.goto('/login');
    await expect(page.locator('html')).toHaveAttribute('dir', 'ltr');
    await page.getByLabel('Email').fill('typed@example.com');
    await page.getByTestId('lang-switch').click();
    await expect(page.locator('html')).toHaveAttribute('lang', 'ar');
    await expect(page.locator('html')).toHaveAttribute('dir', 'rtl');
    await expect(page.getByRole('heading', { level: 1 })).toHaveText('تسجيل الدخول');
    await expect(page.locator('#email')).toHaveValue('typed@example.com');
    // The choice is remembered after a reload.
    // الاختيار ينحفظ بعد إعادة التحميل.
    await page.reload();
    await expect(page.locator('html')).toHaveAttribute('dir', 'rtl');
  });

  test('server errors are shown in Arabic', async ({ page }) => {
    await page.goto('/login');
    await page.getByTestId('lang-switch').click();
    await page.getByLabel('البريد الإلكتروني').fill('nobody@e2e.example');
    await page.getByLabel('كلمة المرور').fill('Whatever-123');
    await page.getByTestId('login-submit').click();
    await expect(page.getByTestId('error-alert')).toContainText('غير صحيحة');
  });

  for (const lang of ['en', 'ar'] as const) {
    test(`no sideways scrolling on a phone in ${lang}`, async ({ page, request }) => {
      await page.setViewportSize({ width: 375, height: 760 });
      const employer = await createAccount(request, 'EMPLOYER');
      const jobId = await publishJob(request, employer, {
        title: 'A rather long job title to test how narrow screens wrap text in cards and headers',
      });

      const checkOverflow = async (path: string) => {
        await page.goto(path);
        await page.waitForLoadState('networkidle');
        const overflow = await page.evaluate(
          () => document.documentElement.scrollWidth - document.documentElement.clientWidth,
        );
        expect(overflow, `${path} overflows by ${overflow}px`).toBeLessThanOrEqual(0);
      };

      await page.goto('/');
      if (lang === 'ar') await page.getByTestId('lang-switch').click();
      for (const path of [
        '/',
        '/jobs',
        `/jobs/${jobId}`,
        '/login',
        '/register',
        '/forgot-password',
      ]) {
        await checkOverflow(path);
      }

      await signInOk(page, employer);
      await expect(page).toHaveURL(/\/employer\/jobs$/);
      for (const path of [
        '/employer/jobs',
        '/employer/jobs/new',
        `/employer/jobs/${jobId}/applicants`,
      ]) {
        await checkOverflow(path);
      }
      await page.getByTestId('logout').click();

      await signInOk(page, ADMIN);
      await expect(page).toHaveURL(/\/admin$/);
      for (const path of ['/admin', '/admin/users', '/admin/emails']) {
        await checkOverflow(path);
      }
    });
  }
});
