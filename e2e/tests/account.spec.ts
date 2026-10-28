import { expect, test } from '@playwright/test';

import {
  PASSWORD,
  createAccount,
  linkIn,
  pathOf,
  signIn,
  signInOk,
  uniqueEmail,
  waitForMail,
} from './helpers';

test.describe('accounts and emails', () => {
  test('register, activate from the email link, then sign in', async ({ page, request }) => {
    const email = uniqueEmail('new-candidate');

    await page.goto('/register');
    await page.getByLabel('Full name').fill('Sara Candidate');
    await page.getByLabel('Email').fill(email);
    await page.getByLabel('Password', { exact: true }).fill(PASSWORD);
    await page.getByLabel('Confirm password').fill(PASSWORD);
    await page.getByTestId('register-submit').click();
    await expect(page.getByTestId('check-email-text')).toContainText(email);

    // Before activation the right password is still refused, with a way to get a new link.
    // قبل التفعيل كلمة المرور الصحيحة تنرفض، مع طريقة نطلب رابط جديد.
    await signIn(page, { email, password: PASSWORD });
    await expect(page.getByTestId('error-alert')).toContainText('not verified');

    const mail = await waitForMail(request, email, 'Verify');
    await page.goto(pathOf(linkIn(mail, 'verify-email')));
    await expect(page.getByTestId('verify-done')).toBeVisible();

    // The same link cannot be used twice. (Leave the page first: only the #fragment would not reload the app.)
    // نفس الرابط ما ينفع مرتين. (نطلع من الصفحة أول: تغيّر الـ #fragment لحاله ما يعيد تحميل التطبيق.)
    await page.goto('/');
    await page.goto(pathOf(linkIn(mail, 'verify-email')));
    await expect(
      page.getByTestId('verify-already').or(page.getByTestId('verify-invalid')),
    ).toBeVisible();

    await signInOk(page, { email, password: PASSWORD });
    await expect(page).toHaveURL(/\/candidate\/applications$/);
    await expect(page.getByTestId('applications-empty')).toBeVisible();
    await expect(page.getByTestId('user-name')).toHaveText('Sara Candidate');
  });

  test('registering an email that already exists gives the same answer (no account enumeration)', async ({
    page,
    request,
  }) => {
    const existing = await createAccount(request, 'CANDIDATE');
    await page.goto('/register');
    await page.getByLabel('Full name').fill('Someone Else');
    await page.getByLabel('Email').fill(existing.email);
    await page.getByLabel('Password', { exact: true }).fill(PASSWORD);
    await page.getByLabel('Confirm password').fill(PASSWORD);
    await page.getByTestId('register-submit').click();
    await expect(page.getByTestId('check-email-text')).toContainText(existing.email);
    // The owner of the address is told by email instead.
    // صاحب العنوان ينبَّه بالإيميل بدلها.
    await waitForMail(request, existing.email, 'already have');
  });

  test('forgot password, reset from the email link, and the link works once', async ({
    page,
    request,
  }) => {
    const account = await createAccount(request, 'CANDIDATE');

    await page.goto('/forgot-password');
    await page.getByLabel('Email').fill(account.email);
    await page.getByTestId('forgot-submit').click();
    await expect(page.getByTestId('forgot-sent')).toBeVisible();

    const mail = await waitForMail(request, account.email, 'Reset');
    const resetPath = pathOf(linkIn(mail, 'reset-password'));
    const newPassword = 'Brand-new-1';
    await page.goto(resetPath);
    await page.getByLabel('Password', { exact: true }).fill(newPassword);
    await page.getByLabel('Confirm password').fill(newPassword);
    await page.getByTestId('reset-submit').click();
    await expect(page.getByTestId('reset-note')).toBeVisible();

    await waitForMail(request, account.email, 'was changed');
    await signInOk(page, { email: account.email, password: newPassword });
    await expect(page).toHaveURL(/\/candidate\/applications$/);

    // Using the same link again fails and points to a new request.
    // استخدام نفس الرابط من جديد يفشل ويدل على طلب جديد.
    await page.getByTestId('logout').click();
    await page.goto(resetPath);
    await page.getByLabel('Password', { exact: true }).fill('Another-pass-2');
    await page.getByLabel('Confirm password').fill('Another-pass-2');
    await page.getByTestId('reset-submit').click();
    await expect(page.getByTestId('error-alert')).toContainText('not valid');
    await expect(page.getByTestId('request-new')).toBeVisible();
  });

  test('a wrong password shows one generic message', async ({ page, request }) => {
    const account = await createAccount(request, 'CANDIDATE');
    await signIn(page, { email: account.email, password: 'Wrong-pass-9' });
    await expect(page.getByTestId('error-alert')).toContainText('Wrong email or password');
  });

  test('pages for roles are closed to visitors and other roles', async ({ page, request }) => {
    await page.goto('/admin');
    await expect(page).toHaveURL(/\/login$/);

    const candidate = await createAccount(request, 'CANDIDATE');
    await signInOk(page, candidate);
    await expect(page).toHaveURL(/\/candidate\/applications$/);
    await page.goto('/employer/jobs');
    await expect(page).toHaveURL(/\/candidate\/applications$/);
    await page.goto('/admin');
    await expect(page).toHaveURL(/\/candidate\/applications$/);
  });
});
