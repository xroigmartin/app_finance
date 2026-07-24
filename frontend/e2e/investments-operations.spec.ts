import { expect, test } from '@playwright/test';
import { post } from './fixtures/seed';

test.describe('Inversión — Operaciones', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/investments/operations');
    await expect(page.getByRole('option', { name: /Cartera E2E/ })).toBeAttached();
  });

  test('la pestaña de dividendos también dibuja su gráfico con contenido real', async ({ page }) => {
    await page.getByRole('button', { name: 'Dividendos' }).click();
    const canvas = page.locator('.tabs-card canvas');
    const box = await canvas.boundingBox();
    expect(box?.width ?? 0).toBeGreaterThan(0);
    expect(box?.height ?? 0).toBeGreaterThan(0);
    const paintedPixels = await canvas.evaluate((el: HTMLCanvasElement) => {
      const ctx = el.getContext('2d')!;
      const { data } = ctx.getImageData(0, 0, el.width, el.height);
      let painted = 0;
      for (let j = 3; j < data.length; j += 4) if (data[j] !== 0) painted++;
      return painted;
    });
    expect(paintedPixels, 'el gráfico de dividendos no tiene ningún píxel pintado').toBeGreaterThan(0);
  });

  test('alta, edición y borrado de una operación manual', async ({ page }) => {
    // No es un <dialog> nativo: se escopa por el propio elemento custom de
    // Angular, que envuelve tanto el botón como el overlay del formulario.
    const opForm = () => page.locator('app-investment-transaction-dialog');

    await page.getByRole('button', { name: '+ Operación' }).click();
    // Se selecciona por el atributo name (estable) en vez de por label: el
    // <select> de tipo usa [ngValue], que Angular codifica como "8: DEPOSIT",
    // no "DEPOSIT" literal, y por accesibilidad daba timeouts intermitentes.
    await opForm().locator('select[name="type"]').selectOption({ label: 'Aportación' });
    await opForm().locator('input[name="tradeDate"]').fill('2026-03-01');
    await opForm().locator('input[name="amount"]').fill('123');
    await opForm().getByRole('button', { name: 'Guardar' }).click();
    await expect(opForm().locator('.overlay')).toHaveCount(0);

    const row = page.locator('tbody tr', { hasText: '01/03/2026' });
    await expect(row).toBeVisible();
    await row.getByRole('button', { name: 'Editar' }).click();
    await opForm().locator('input[name="amount"]').fill('456');
    await opForm().getByRole('button', { name: 'Guardar' }).click();
    await expect(page.locator('tbody tr', { hasText: '01/03/2026' })).toContainText('456');

    page.once('dialog', d => d.accept());
    await page.locator('tbody tr', { hasText: '01/03/2026' }).getByRole('button', { name: 'Borrar' }).click();
    await expect(page.locator('tbody tr', { hasText: '01/03/2026' })).toHaveCount(0);
  });

  test('cambiar el tamaño de página en Operaciones no deja una carrera con el tamaño anterior (regresión)', async ({ page }) => {
    // Mismo bug/fix que en Movimientos (transactions.spec.ts): app-pagination
    // emitía pageChange(0) además de sizeChange, disparando dos recargas
    // independientes donde la del pageChange leía el tamaño ANTIGUO. Solo se
    // reproduce contra el backend real; cartera propia para no depender de lo
    // que dejen otros tests de este fichero.
    const portfolio = await post<{ id: number }>('/api/investments/portfolios', {
      name: 'Paginación Operaciones E2E', baseCurrency: 'EUR',
    });
    for (let i = 1; i <= 12; i++) {
      await post(`/api/investments/portfolios/${portfolio.id}/transactions`, {
        type: 'DEPOSIT',
        tradeDate: `2026-05-${String(i).padStart(2, '0')}`,
        amount: 10 + i,
        currency: 'EUR',
      });
    }

    await page.reload();
    await expect(page.getByRole('option', { name: /Paginación Operaciones E2E/ })).toBeAttached();
    // El <option> lleva "Nombre (divisa)" (ver investment-toolbar.ts), no el nombre a secas.
    await page.getByLabel('Cartera').selectOption({ label: 'Paginación Operaciones E2E (EUR)' });
    await expect(page.locator('.tabs-card tbody tr')).toHaveCount(12);

    const sizeSelect = page.getByLabel('Por página');

    await sizeSelect.selectOption('5');
    await expect(page.locator('.tabs-card tbody tr')).toHaveCount(5);
    await expect(page.locator('.page-indicator')).toHaveText('Página 1 de 3');

    await sizeSelect.selectOption('10');
    await expect(page.locator('.tabs-card tbody tr')).toHaveCount(10);
    await expect(page.locator('.page-indicator')).toHaveText('Página 1 de 2');

    await sizeSelect.selectOption('25');
    await expect(page.locator('.tabs-card tbody tr')).toHaveCount(12);
    await expect(page.locator('.page-indicator')).toHaveText('Página 1 de 1');
  });
});
