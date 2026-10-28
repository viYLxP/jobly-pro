import { APIRequestContext, Page, expect } from '@playwright/test';

const MAILPIT = process.env.MAILPIT_URL ?? 'http://localhost:8025';

export const PASSWORD = 'Passw0rd-e2e';

/** A fresh address per test, so tests never see each other's emails. / إيميل جديد لكل اختبار. */
export function uniqueEmail(prefix: string): string {
  return `${prefix}-${Date.now()}-${Math.random().toString(36).slice(2, 7)}@e2e.example`;
}

export interface Mail {
  subject: string;
  text: string;
}

/**
 * Waits for an email to a given address (and optionally a subject) to show up in Mailpit. The backend sends emails
 * from a queue every few seconds, so this polls.
 * ينتظر ظهور إيميل لعنوان معيّن (وعنوان رسالة اختياري) في Mailpit. الباكند يرسل من طابور كل كم ثانية، فنسأل بتكرار.
 */
export async function waitForMail(
  request: APIRequestContext,
  to: string,
  subjectPart = '',
  timeoutMs = 25_000,
): Promise<Mail> {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    const search = await request.get(`${MAILPIT}/api/v1/search`, {
      params: { query: `to:"${to}"` },
    });
    const body = (await search.json()) as { messages?: { ID: string; Subject: string }[] };
    const found = (body.messages ?? []).find((m) => m.Subject.includes(subjectPart));
    if (found) {
      const full = (await (await request.get(`${MAILPIT}/api/v1/message/${found.ID}`)).json()) as {
        Text: string;
      };
      return { subject: found.Subject, text: full.Text };
    }
    await new Promise((resolve) => setTimeout(resolve, 500));
  }
  throw new Error(
    `No email to ${to} with subject containing "${subjectPart}" after ${timeoutMs} ms`,
  );
}

/** Pulls the app link with its #secret out of an email's text, e.g. linkIn(mail, 'verify-email'). */
export function linkIn(mail: Mail, page: string): string {
  const match = mail.text.match(new RegExp(`https?://[^\\s"<>\\]]+/${page}#[A-Za-z0-9._-]+`));
  if (!match) throw new Error(`No ${page} link in email "${mail.subject}":\n${mail.text}`);
  return match[0];
}

/** Turns a link from an email (which points at PUBLIC_URL) into a path on the app under test. */
export function pathOf(link: string): string {
  const url = new URL(link);
  return url.pathname + url.hash;
}

export interface Account {
  email: string;
  password: string;
}

/** Registers and activates an account through the API (the UI flow is tested separately). */
export async function createAccount(
  request: APIRequestContext,
  role: 'CANDIDATE' | 'EMPLOYER',
  prefix = role.toLowerCase(),
): Promise<Account> {
  const email = uniqueEmail(prefix);
  const response = await request.post('/api/auth/register', {
    data: {
      email,
      password: PASSWORD,
      fullName: role === 'EMPLOYER' ? 'E2E Employer' : 'E2E Candidate',
      role,
      companyName: role === 'EMPLOYER' ? 'E2E Company' : null,
      language: 'en',
    },
  });
  expect(response.status()).toBe(202);
  const mail = await waitForMail(request, email, 'Verify');
  const token = new URL(linkIn(mail, 'verify-email')).hash.slice(1);
  const verified = await request.post('/api/auth/verify-email', { data: { token } });
  expect(verified.ok()).toBe(true);
  return { email, password: PASSWORD };
}

export async function signIn(page: Page, account: Account): Promise<void> {
  await page.goto('/login');
  // By id, not by label, so it works in both languages.
  // بالـ id مو بالعنوان، فيشتغل بالعربي والإنجليزي.
  await page.locator('#email').fill(account.email);
  await page.locator('#password').fill(account.password);
  await page.getByTestId('login-submit').click();
}

/** Signs in and waits until the header shows the user, so the next step never races the redirect. */
export async function signInOk(page: Page, account: Account): Promise<void> {
  await signIn(page, account);
  await expect(page.getByTestId('user-name')).toBeVisible();
}

/** Signs in through the API and returns the bearer token, for setup steps that are not under test. */
export async function apiToken(request: APIRequestContext, account: Account): Promise<string> {
  const response = await request.post('/api/auth/login', {
    data: { email: account.email, password: account.password },
  });
  expect(response.ok()).toBe(true);
  return ((await response.json()) as { token: string }).token;
}

/** Creates and publishes a job as the given employer through the API; returns its id. */
export async function publishJob(
  request: APIRequestContext,
  employer: Account,
  job: { title: string; city?: string; description?: string },
): Promise<number> {
  const headers = { Authorization: `Bearer ${await apiToken(request, employer)}` };
  const created = await request.post('/api/employer/jobs', {
    headers,
    data: {
      title: job.title,
      city: job.city ?? 'Riyadh',
      description: job.description ?? 'A job created by the end-to-end tests.',
      type: 'FULL_TIME',
      salaryMin: 9000,
      salaryMax: 12000,
    },
  });
  expect(created.status()).toBe(201);
  const id = ((await created.json()) as { id: number }).id;
  const published = await request.post(`/api/employer/jobs/${id}/publish`, { headers });
  expect(published.ok()).toBe(true);
  return id;
}
