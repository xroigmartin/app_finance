import { expect, test } from '@playwright/test';

test.describe('Inversión — Posiciones', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/investments/positions');
    await expect(page.getByRole('option', { name: /Cartera E2E/ })).toBeAttached();
  });

  test('muestra la posición sembrada en la tabla', async ({ page }) => {
    await expect(page.locator('.positions-card').getByText('Empresa de Pruebas E2E')).toBeVisible();
  });

  test('las cabeceras TWR y XIRR tienen un tooltip explicando la métrica', async ({ page }) => {
    const twrInfo = page.locator('th', { hasText: 'TWR' }).getByRole('button', { name: 'Qué es el TWR' });
    const xirrInfo = page.locator('th', { hasText: 'XIRR' }).getByRole('button', { name: 'Qué es el XIRR' });
    await expect(twrInfo).toHaveAttribute('title', /.+/);
    await expect(xirrInfo).toHaveAttribute('title', /.+/);
  });
});
