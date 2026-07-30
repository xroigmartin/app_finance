import path from 'node:path';
import { expect, test } from '@playwright/test';
import { post } from './fixtures/seed';

const FLEX_FIXTURE = path.join(__dirname, 'fixtures', 'flex-sample.xml');
const FLEX_WARNING_FIXTURE = path.join(__dirname, 'fixtures', 'flex-sample-warning.xml');

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

  test('importar un Flex real deja rastro en la pestaña Importaciones (RF-11)', async ({ page }) => {
    const portfolio = await post<{ id: number }>('/api/investments/portfolios', {
      name: 'Historial de imports E2E', baseCurrency: 'EUR',
    });
    await page.reload();
    await expect(page.getByRole('option', { name: /Historial de imports E2E/ })).toBeAttached();
    await page.getByLabel('Cartera').selectOption({ label: `Historial de imports E2E (EUR)` });

    await page.getByRole('button', { name: 'Importar Flex' }).click();
    await page.locator('input[type="file"]').setInputFiles(FLEX_FIXTURE);
    await page.getByRole('button', { name: 'Importar', exact: true }).click();
    await expect(page.getByText(/operaciones importadas/)).toBeVisible();
    await page.getByRole('button', { name: 'Cerrar' }).click();

    await page.getByRole('button', { name: 'Importaciones' }).click();
    const row = page.locator('tbody tr', { hasText: 'flex-sample.xml' });
    await expect(row).toBeVisible();
    await expect(row.locator('td').nth(3)).toHaveText('11'); // importadas
    await expect(row.locator('td').nth(4)).toHaveText('0'); // duplicadas
    await expect(row.locator('td').nth(5)).toHaveText('3'); // errores

    await row.getByRole('button', { name: 'Ver detalle' }).click();
    await expect(page.locator('.history-detail .errors li')).toHaveCount(3);

    // Reimportar el mismo fichero: 0 nuevas, todo duplicado, se sigue registrando (§1 del plan).
    await page.getByRole('button', { name: 'Importar Flex' }).click();
    await page.locator('input[type="file"]').setInputFiles(FLEX_FIXTURE);
    await page.getByRole('button', { name: 'Importar', exact: true }).click();
    await expect(page.getByText(/operaciones importadas/)).toBeVisible();
    await page.getByRole('button', { name: 'Cerrar' }).click();

    await expect(page.locator('tbody tr', { hasText: 'flex-sample.xml' })).toHaveCount(2);

    // Un segundo fichero con una venta sin posición previa (RN-4, lado
    // "blando" — solo se endurece a rechazo en el alta manual, ver
    // InvestmentTransactionService): también deja rastro, esta vez con avisos
    // en vez de errores, ejercitando la rama <ul class="warnings"> del detalle.
    await page.getByRole('button', { name: 'Importar Flex' }).click();
    await page.locator('input[type="file"]').setInputFiles(FLEX_WARNING_FIXTURE);
    await page.getByRole('button', { name: 'Importar', exact: true }).click();
    await expect(page.getByText(/operaciones importadas/)).toBeVisible();
    await page.getByRole('button', { name: 'Cerrar' }).click();

    const warningRow = page.locator('tbody tr', { hasText: 'flex-sample-warning.xml' });
    await expect(warningRow).toBeVisible();
    await expect(warningRow.locator('td').nth(3)).toHaveText('1'); // importadas
    await expect(warningRow.locator('td').nth(6)).toHaveText('1'); // avisos
    await warningRow.getByRole('button', { name: 'Ver detalle' }).click();
    await expect(page.locator('.history-detail .warnings li')).toHaveCount(1);
    await expect(page.locator('.history-detail .warnings li')).toContainText('posición suficiente');
  });
});
