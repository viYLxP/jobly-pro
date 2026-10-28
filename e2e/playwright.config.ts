import { defineConfig, devices } from '@playwright/test';

// The tests drive the real app: Angular on E2E_BASE_URL (its server forwards /api to Spring Boot) and Mailpit,
// which catches the emails the backend sends. Start them first (see ../README.md, "End-to-end tests").
// الاختبارات تشغّل التطبيق الحقيقي: Angular على E2E_BASE_URL (خادمه يحوّل /api إلى Spring Boot) و Mailpit
// اللي يلتقط الإيميلات اللي يرسلها الباكند. شغّلهم أول (شوف ../README.md).
export default defineConfig({
  testDir: './tests',
  timeout: 60_000,
  expect: { timeout: 10_000 },
  fullyParallel: true,
  workers: process.env.CI ? 2 : 3,
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? [['list'], ['html', { open: 'never' }]] : 'list',
  use: {
    baseURL: process.env.E2E_BASE_URL ?? 'http://localhost:4200',
    locale: 'en-US',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    launchOptions: {
      // Lets a machine with a pre-installed Chromium run the tests without downloading one.
      // يخلي جهاز عنده Chromium جاهز يشغّل الاختبارات بدون تنزيل واحد.
      executablePath: process.env.PW_CHROMIUM_PATH || undefined,
    },
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
});
