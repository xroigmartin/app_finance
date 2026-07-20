import { defineConfig, devices } from '@playwright/test';
import { E2E_FRONTEND_PORT, frontendUrl } from './e2e/env';

/**
 * E2E contra la app real (frontend + backend + Postgres), aislada por completo
 * del stack de desarrollo: ver docs/testing-plan-frontend.md CP2. globalSetup
 * resetea/siembra la BD e2e y arranca el backend e2e; aquí solo se arranca el
 * `ng serve` de test (puerto E2E_FRONTEND_PORT, proxy propio hacia el backend e2e).
 */
export default defineConfig({
  testDir: './e2e',
  testMatch: '**/*.spec.ts',
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  workers: 1,
  reporter: [['list'], ['html', { open: 'never' }]],
  globalSetup: './e2e/global-setup.ts',
  globalTeardown: './e2e/global-teardown.ts',
  use: {
    baseURL: frontendUrl,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [
    { name: 'chromium', use: { ...devices['Desktop Chrome'] } },
  ],
  webServer: {
    command: `npx ng serve --port ${E2E_FRONTEND_PORT} --proxy-config proxy.conf.e2e.json`,
    url: frontendUrl,
    reuseExistingServer: false,
    timeout: 120_000,
  },
});
