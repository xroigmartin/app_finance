import { expect, test } from '@playwright/test';

test('la app carga con datos reales: dashboard e inversión', async ({ page }) => {
  await page.goto('/');

  await expect(page.getByRole('heading', { name: 'Panel general' })).toBeVisible();
  // Cuenta sembrada en el globalSetup (e2e/fixtures/seed.ts) contra el backend real.
  await expect(page.getByRole('option', { name: 'Cuenta Corriente E2E' })).toBeAttached();
  await expect(page.locator('.kpi-value').first()).toBeVisible();

  await page.getByRole('link', { name: 'Inversión' }).click();
  await expect(page.getByRole('heading', { name: 'Inversión' })).toBeVisible();
  await expect(page.getByRole('option', { name: /Cartera E2E/ })).toBeAttached();
});
