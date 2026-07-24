import { expect, test } from '@playwright/test';

test.describe('Inversión — Panel general', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/investments/dashboard');
    await expect(page.getByRole('option', { name: /Cartera E2E/ })).toBeAttached();
  });

  test('regresión: los gráficos de la cartera tienen contenido real, no están en blanco', async ({ page }) => {
    for (const heading of [
      'Evolución: valor vs aportado', 'Asignación de la cartera',
      'P&L latente por posición', 'Rentabilidad por posición (%)',
    ]) {
      const canvas = page.locator('.chart-card', { hasText: heading }).locator('canvas');
      const box = await canvas.boundingBox();
      expect(box?.width ?? 0).toBeGreaterThan(0);
      expect(box?.height ?? 0).toBeGreaterThan(0);
      // La geometría no basta: Chart.js puede quedarse con el tamaño por defecto
      // del <canvas> (300×150) si el gráfico se destruye y recrea demasiado
      // rápido (varias cargas asíncronas disparando renderCharts() casi a la
      // vez) y nunca llega a medir su contenedor real. Se comprueba que tiene
      // píxeles pintados de verdad.
      const paintedPixels = await canvas.evaluate((el: HTMLCanvasElement) => {
        const ctx = el.getContext('2d')!;
        const { data } = ctx.getImageData(0, 0, el.width, el.height);
        let painted = 0;
        for (let j = 3; j < data.length; j += 4) if (data[j] !== 0) painted++;
        return painted;
      });
      expect(paintedPixels, `«${heading}» no tiene ningún píxel pintado`).toBeGreaterThan(0);
    }
  });

  test('muestra la posición sembrada en la tabla', async ({ page }) => {
    await expect(page.locator('.positions-card').getByText('Empresa de Pruebas E2E')).toBeVisible();
  });

  test('crea una cartera nueva desde la barra de herramientas', async ({ page }) => {
    await page.getByRole('button', { name: 'Nueva cartera' }).click();
    await page.getByPlaceholder('Nombre de la cartera').fill('Cartera Nueva E2E');
    await page.getByPlaceholder('EUR').fill('USD');
    await page.getByRole('button', { name: 'Crear' }).click();
    await expect(page.getByRole('option', { name: /Cartera Nueva E2E/ })).toBeAttached();
  });
});
