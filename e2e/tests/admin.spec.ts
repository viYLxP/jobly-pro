import { expect, test } from '@playwright/test';

import { createAccount, signIn, signInOk } from './helpers';

const ADMIN = {
  email: 'admin@jobly.example',
  password: process.env.ADMIN_PASSWORD ?? 'Admin@12345',
};

test.describe('admin', () => {
  test('suspend and restore a user, and see the email outbox', async ({
    page,
    browser,
    request,
  }) => {
    const candidate = await createAccount(request, 'CANDIDATE');

    await signInOk(page, ADMIN);
    await expect(page).toHaveURL(/\/admin$/);
    await expect(page.getByTestId('stat-alerts')).toBeVisible();

    await page.getByTestId('tab-users').click();
    await page.getByTestId('users-search').fill(candidate.email);
    await page.getByTestId('users-search').press('Enter');
    const row = page.getByTestId('user-row').filter({ hasText: candidate.email });
    await expect(row).toHaveCount(1);
    await row.getByTestId('user-suspend').click();
    await page.getByRole('dialog').getByRole('button', { name: 'Suspend', exact: true }).click();
    await expect(row).toContainText('Suspended');

    // A suspended user cannot sign in, even with the right password.
    // المستخدم الموقوف ما يقدر يدخل حتى بكلمة مرور صحيحة.
    const other = await (await browser.newContext()).newPage();
    await signIn(other, candidate);
    await expect(other.getByTestId('error-alert')).toContainText('suspended');

    await row.getByTestId('user-restore').click();
    await page.getByRole('dialog').getByRole('button', { name: 'Restore', exact: true }).click();
    await expect(row).toContainText('Active');
    await signInOk(other, candidate);
    await expect(other).toHaveURL(/\/candidate\/applications$/);

    await page.getByTestId('tab-emails').click();
    await expect(page.getByTestId('email-row').first()).toBeVisible();
    await page.getByTestId('email-filter-FAILED').click();
    await expect(page.getByTestId('email-filter-FAILED')).toHaveAttribute('aria-pressed', 'true');
  });
});
