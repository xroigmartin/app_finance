import { expect, test } from '@playwright/test';

test.describe('Dashboard', () => {
  test('carga con datos reales: KPIs, cuenta sembrada y últimos movimientos', async ({ page }) => {
    await page.goto('/');

    await expect(page.getByRole('heading', { name: 'Panel general' })).toBeVisible();
    await expect(page.getByRole('option', { name: 'Cuenta Corriente E2E' })).toBeAttached();
    await expect(page.locator('.kpi-value').first()).toBeVisible();
    await expect(page.getByText('Nómina julio')).toBeVisible();
    await expect(page.getByText('Alquiler')).toBeVisible();
  });

  test('regresión: los 7 gráficos del panel tienen contenido real, no están en blanco', async ({ page }) => {
    await page.goto('/');
    const canvases = page.locator('.chart-card canvas');
    await expect(canvases).toHaveCount(7);
    const count = await canvases.count();
    for (let i = 0; i < count; i++) {
      const box = await canvases.nth(i).boundingBox();
      expect(box?.width ?? 0).toBeGreaterThan(0);
      expect(box?.height ?? 0).toBeGreaterThan(0);
    }
  });

  test('navegación de mes: adelante/atrás cambia la etiqueta del periodo', async ({ page }) => {
    await page.goto('/');
    const monthLabel = page.locator('.month-label');
    const initial = await monthLabel.textContent();
    await page.locator('.month-nav').getByRole('button', { name: '‹' }).click();
    await expect(monthLabel).not.toHaveText(initial ?? '');
    await page.locator('.month-nav').getByRole('button', { name: '›' }).click();
    await expect(monthLabel).toHaveText(initial ?? '');
  });

  test('cambia el tema claro/oscuro', async ({ page }) => {
    await page.goto('/');
    const html = page.locator('html');
    const before = await html.getAttribute('data-theme');
    await page.locator('.theme-btn').click();
    await expect(html).not.toHaveAttribute('data-theme', before ?? '');
  });

  test('la barra lateral se puede colapsar y volver a mostrar', async ({ page }) => {
    await page.goto('/');
    await page.getByRole('button', { name: 'Ocultar menú' }).click();
    await expect(page.locator('.layout')).toHaveClass(/collapsed/);
    await page.getByRole('button', { name: 'Mostrar menú' }).click();
    await expect(page.locator('.layout')).not.toHaveClass(/collapsed/);
  });

  test('navega a Inversión desde el menú', async ({ page }) => {
    await page.goto('/');
    await page.getByRole('link', { name: 'Inversión' }).click();
    await expect(page.getByRole('heading', { name: 'Inversión' })).toBeVisible();
    await expect(page.getByRole('option', { name: /Cartera E2E/ })).toBeAttached();
  });
});
