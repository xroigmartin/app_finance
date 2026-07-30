import path from 'node:path';
import { expect, test } from '@playwright/test';
import { post } from './fixtures/seed';

const FLEX_FIXTURE = path.join(__dirname, 'fixtures', 'flex-sample.xml');

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

  test('los gráficos de P&L latente y rentabilidad por posición tienen un tooltip explicativo en el título', async ({ page }) => {
    for (const heading of ['P&L latente por posición', 'Rentabilidad por posición (%)']) {
      const info = page.locator('.chart-card', { hasText: heading })
        .getByRole('button', { name: 'Qué muestra este gráfico' });
      await expect(info).toHaveAttribute('title', /.+/);
    }
  });

  test('crea una cartera nueva desde la barra de herramientas', async ({ page }) => {
    await page.getByRole('button', { name: 'Nueva cartera' }).click();
    await page.getByPlaceholder('Nombre de la cartera').fill('Cartera Nueva E2E');
    await page.getByPlaceholder('EUR').fill('USD');
    await page.getByRole('button', { name: 'Crear' }).click();
    await expect(page.getByRole('option', { name: /Cartera Nueva E2E/ })).toBeAttached();
  });

  test('un import con errores desde el Panel general enlaza al detalle en Importaciones (RF-12)', async ({ page }) => {
    // El diálogo de import vive en la barra de herramientas compartida con
    // Operaciones (investment-toolbar.ts): desde el Panel general, tras un
    // import con errores, el enlace debe cerrar el diálogo y cruzar de página
    // dejando la pestaña Importaciones ya abierta con el detalle a la vista —
    // justo lo que un test unitario de un solo componente no puede probar.
    const portfolio = await post<{ id: number }>('/api/investments/portfolios', {
      name: 'Enlace import Dashboard E2E', baseCurrency: 'EUR',
    });
    await page.reload();
    await expect(page.getByRole('option', { name: /Enlace import Dashboard E2E/ })).toBeAttached();
    await page.getByLabel('Cartera').selectOption({ label: 'Enlace import Dashboard E2E (EUR)' });

    await page.getByRole('button', { name: 'Importar Flex' }).click();
    await page.locator('input[type="file"]').setInputFiles(FLEX_FIXTURE);
    await page.getByRole('button', { name: 'Importar', exact: true }).click();
    await expect(page.getByText(/operaciones importadas/)).toBeVisible();

    await page.getByRole('button', { name: /Ver detalle en Importaciones/ }).click();

    await expect(page).toHaveURL(/\/investments\/operations$/);
    const row = page.locator('tbody tr', { hasText: 'flex-sample.xml' });
    await expect(row).toBeVisible();
    await expect(row.locator('td').nth(5)).toHaveText('3'); // errores
  });
});
