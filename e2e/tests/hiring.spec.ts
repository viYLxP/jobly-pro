import { expect, test } from '@playwright/test';

import { createAccount, linkIn, pathOf, publishJob, signInOk, waitForMail } from './helpers';

test.describe('hiring', () => {
  test('employer posts a job, candidate applies, status changes arrive by email', async ({
    page,
    browser,
    request,
  }) => {
    const employer = await createAccount(request, 'EMPLOYER');
    const candidate = await createAccount(request, 'CANDIDATE');
    const title = `QA Engineer ${Date.now()}`;

    // The employer saves a draft, then publishes it.
    // صاحب العمل يحفظ مسودة ثم ينشرها.
    await signInOk(page, employer);
    await expect(page).toHaveURL(/\/employer\/jobs$/);
    await page.getByTestId('job-new').click();
    await page.getByTestId('job-title-input').fill(title);
    await page.getByTestId('job-city-input').fill('Dammam');
    await page.getByTestId('job-description-input').fill('Test the product end to end.');
    await page.getByTestId('job-save').click();
    const row = page.getByTestId('employer-job').filter({ hasText: title });
    await expect(row).toContainText('Draft');
    await row.getByTestId('job-publish').click();
    await page.getByRole('dialog').getByRole('button', { name: 'Publish', exact: true }).click();
    await expect(row).toContainText('Published');

    // The candidate finds it by keyword and applies.
    // المرشح يلقاها بكلمة بحث ويقدّم.
    const candidatePage = await (await browser.newContext()).newPage();
    await signInOk(candidatePage, candidate);
    await candidatePage.getByTestId('nav-jobs').click();
    await candidatePage.getByTestId('search-q').fill(title);
    await candidatePage.getByTestId('search-submit').click();
    await expect(candidatePage.getByTestId('job-count')).toContainText('Jobs found: 1');
    await candidatePage.getByTestId('job-card').click();
    await expect(candidatePage.getByTestId('job-title')).toHaveText(title);
    await candidatePage.locator('#cover').fill('I love testing.');
    await candidatePage.getByTestId('apply-submit').click();
    await expect(candidatePage.getByTestId('apply-success')).toBeVisible();
    await waitForMail(request, candidate.email, 'We received your application');
    await waitForMail(request, employer.email, 'New applicant');

    // Applying twice is refused.
    // التقديم مرتين مرفوض.
    await candidatePage.reload();
    await candidatePage.locator('#cover').fill('Again');
    await candidatePage.getByTestId('apply-submit').click();
    await expect(candidatePage.getByTestId('error-alert')).toContainText('already applied');

    // The employer opens the applicants, moves the application, and the candidate is emailed.
    // صاحب العمل يفتح المتقدمين وينقل الطلب، والمرشح يوصله إيميل.
    await page.reload();
    await row.getByRole('link', { name: /1 applicants/ }).click();
    const applicant = page.getByTestId('applicant');
    await expect(applicant).toContainText('E2E Candidate');
    await expect(applicant).toContainText('I love testing.');
    await applicant.getByTestId('move-REVIEWING').click();
    await expect(applicant.getByTestId('move-INTERVIEW')).toBeVisible();
    const statusMail = await waitForMail(request, candidate.email, 'Update on your application');
    expect(statusMail.text).toContain('Under review');

    // A step that skips ahead is not offered.
    // خطوة تقفز مرحلة ما تنعرض.
    await expect(applicant.getByTestId('move-OFFER')).toHaveCount(0);

    // The candidate sees the new status and the timeline.
    // المرشح يشوف الحالة الجديدة والخط الزمني.
    await candidatePage.goto('/candidate/applications');
    const mine = candidatePage.getByTestId('application').filter({ hasText: title });
    await expect(mine).toContainText('Under review');
    await expect(mine.locator('.timeline li')).toHaveCount(2);

    // The employer can filter the list by status.
    // صاحب العمل يفلتر بالحالة.
    await page.getByTestId('filter-OFFER').click();
    await expect(page.getByTestId('applicants-empty')).toBeVisible();
  });

  test('job alert email, then one-click unsubscribe', async ({ page, browser, request }) => {
    const employer = await createAccount(request, 'EMPLOYER');
    const candidate = await createAccount(request, 'CANDIDATE');
    const keyword = `zebra${Date.now()}`;

    const candidatePage = await (await browser.newContext()).newPage();
    await signInOk(candidatePage, candidate);
    await candidatePage.getByTestId('nav-alerts').click();
    await candidatePage.getByTestId('alert-add').click();
    await expect(candidatePage.getByText('Fill at least one field.')).toBeVisible();
    await candidatePage.getByTestId('alert-keyword').fill(keyword);
    await candidatePage.getByTestId('alert-add').click();
    await expect(candidatePage.getByTestId('alert-row')).toContainText(keyword);

    await publishJob(request, employer, { title: `${keyword} engineer` });
    const mail = await waitForMail(request, candidate.email, 'New job for you');
    expect(mail.text).toContain(`${keyword} engineer`);

    await page.goto(pathOf(linkIn(mail, 'unsubscribe')));
    await page.getByTestId('unsubscribe-confirm').click();
    await expect(page.getByTestId('unsubscribe-done')).toBeVisible();

    // The alert is now paused in the candidate's list.
    // التنبيه صار متوقف في قائمة المرشح.
    await candidatePage.reload();
    await expect(candidatePage.getByTestId('alert-row')).toContainText('Paused');
  });

  test('an employer cannot apply, a visitor is asked to sign in', async ({ page, request }) => {
    const employer = await createAccount(request, 'EMPLOYER');
    const id = await publishJob(request, employer, { title: `Visitor view ${Date.now()}` });
    await page.goto(`/jobs/${id}`);
    await expect(page.getByTestId('apply-login')).toBeVisible();
    await signInOk(page, employer);
    await page.goto(`/jobs/${id}`);
    await expect(page.getByText('Only candidate accounts can apply.')).toBeVisible();
  });

  test('a job that does not exist shows a friendly message', async ({ page }) => {
    await page.goto('/jobs/999999999');
    await expect(page.getByTestId('job-missing')).toBeVisible();
  });
});
