import { defineConfig, devices } from '@playwright/test';

/**
 * E2E, визуальные проверки и замер канвы. API подменяется в браузере
 * in-memory сервером на снимке эталонной модели (`e2e/support/mockApi.ts`):
 * тестам не нужны ни бэкенд, ни Keycloak, а команды синхронизации видны
 * как записанные вызовы.
 */
const PORT = 5175;

export default defineConfig({
  testDir: '.',
  testMatch: ['e2e/**/*.spec.ts', 'visual/**/*.spec.ts', 'perf/canvas-pan.bench.ts'],
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? 'github' : 'list',
  use: {
    baseURL: `http://localhost:${PORT}`,
    viewport: { width: 1456, height: 860 },
    locale: 'ru-RU',
    trace: 'retain-on-failure',
  },
  expect: { toHaveScreenshot: { maxDiffPixelRatio: 0.001 } },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'], viewport: { width: 1456, height: 860 } } }],
  webServer: {
    command: `npx vite --port ${PORT} --strictPort`,
    port: PORT,
    reuseExistingServer: !process.env.CI,
  },
});
