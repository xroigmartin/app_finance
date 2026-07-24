import { expect, test } from '@playwright/test';
import { get, post } from './fixtures/seed';

test.describe('Movimientos', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/transactions');
    await expect(page.getByRole('heading', { name: 'Movimientos' })).toBeVisible();
  });

  function dialog(page: import('@playwright/test').Page) {
    return page.locator('dialog[aria-labelledby="form-title"]');
  }

  test('muestra los movimientos y la transferencia sembrados', async ({ page }) => {
    await expect(page.getByText('Nómina julio')).toBeVisible();
    await expect(page.getByText('Alquiler')).toBeVisible();
  });

  test('alta, edición y borrado de un gasto', async ({ page }) => {
    await page.getByRole('button', { name: '+ Nuevo movimiento' }).click();
    const form = dialog(page);
    await form.getByLabel('Fecha').fill('2026-04-15');
    await form.getByLabel('Importe').fill('42');
    await form.getByLabel('Cuenta').selectOption({ label: 'Cuenta Corriente E2E' });
    await form.getByLabel('Categoría').selectOption({ label: 'Alimentación' });
    await form.getByLabel('Descripción').fill('Gasto E2E');
    await form.getByRole('button', { name: 'Guardar' }).click();
    await expect(form).toBeHidden();

    const row = page.locator('tbody tr', { hasText: 'Gasto E2E' });
    await expect(row).toBeVisible();
    await expect(row).toContainText('15/04/2026');

    await row.getByRole('button', { name: 'Editar' }).click();
    await dialog(page).getByLabel('Importe').fill('99');
    await dialog(page).getByRole('button', { name: 'Guardar' }).click();
    await expect(page.locator('tbody tr', { hasText: 'Gasto E2E' })).toContainText('99');

    page.once('dialog', d => d.accept());
    await page.locator('tbody tr', { hasText: 'Gasto E2E' }).getByRole('button', { name: 'Eliminar' }).click();
    await expect(page.locator('tbody tr', { hasText: 'Gasto E2E' })).toHaveCount(0);
  });

  test('crea una transferencia entre las cuentas sembradas', async ({ page }) => {
    await page.getByRole('button', { name: '+ Nuevo movimiento' }).click();
    const form = dialog(page);
    await form.getByLabel('Tipo').selectOption('TRANSFER');
    await form.getByLabel('Fecha').fill('2026-04-16');
    await form.getByLabel('Importe').fill('75');
    await form.getByLabel('Origen').selectOption({ label: 'Cuenta Corriente E2E' });
    await form.getByLabel('Destino').selectOption({ label: 'Ahorro E2E' });
    await form.getByLabel('Descripción').fill('Traspaso E2E');
    await form.getByRole('button', { name: 'Guardar' }).click();
    await expect(form).toBeHidden();

    const row = page.locator('tbody tr', { hasText: 'Traspaso E2E' });
    await expect(row).toBeVisible();
    await expect(row).toContainText('Cuenta Corriente E2E → Ahorro E2E');

    page.once('dialog', d => d.accept());
    await row.getByRole('button', { name: 'Eliminar' }).click();
    await expect(page.locator('tbody tr', { hasText: 'Traspaso E2E' })).toHaveCount(0);
  });

  test('registra una devolución parcial de un gasto pendiente', async ({ page }) => {
    const original = page.locator('tbody tr', { hasText: 'Alquiler' });
    await original.getByRole('button', { name: 'Devolver' }).click();
    const form = dialog(page);
    await expect(form.locator('.refund-info')).toContainText('Vivienda');
    await form.getByLabel('Descripción').fill('Devolución E2E');
    await form.getByRole('button', { name: 'Guardar' }).click();
    await expect(form).toBeHidden();

    const refundRow = page.locator('tbody tr', { hasText: 'Devolución E2E' });
    await expect(refundRow).toBeVisible();
    await expect(refundRow).toContainText('↩ Devolución');
  });

  test('filtra por cuenta', async ({ page }) => {
    await page.locator('.toolbar').getByLabel('Cuenta').selectOption({ label: 'Ahorro E2E' });
    // Sin movimientos para esa cuenta, la tabla renderiza la fila @empty, no cero filas.
    await expect(page.locator('tbody tr')).toHaveCount(1);
    await expect(page.locator('tbody tr')).toContainText('No hay movimientos en este periodo');
  });

  test('cambiar el tamaño de página no deja una carrera con el tamaño anterior (regresión)', async ({ page }) => {
    // Bug real: app-pagination emitía pageChange(0) además de sizeChange, cada
    // uno disparando su propia recarga — la del pageChange leía el tamaño
    // ANTIGUO (onSizeChange aún no había corrido), dos peticiones HTTP
    // concurrentes reales donde la del tamaño antiguo podía ganar la carrera.
    // Solo se reproduce contra el backend real (los mocks síncronos de Vitest
    // no pueden verla), y necesita más filas que cualquier tamaño de página
    // pequeño para que un recuento equivocado sea observable. Cuenta propia y
    // filtrada para no depender del estado que dejen otros tests de este fichero.
    const account = await post<{ id: number }>('/api/accounts', {
      name: 'Paginación E2E', type: 'Banco', initialBalance: 0,
    });
    const categories = await get<{ id: number; name: string }[]>('/api/categories');
    const alimentacion = categories.find(c => c.name === 'Alimentación')!;
    for (let i = 1; i <= 12; i++) {
      await post('/api/transactions', {
        date: `2026-05-${String(i).padStart(2, '0')}`,
        amount: 10 + i,
        description: `Pag E2E ${i}`,
        type: 'EXPENSE',
        accountId: account.id,
        categoryId: alimentacion.id,
      });
    }

    await page.reload();
    await page.locator('.toolbar').getByLabel('Cuenta').selectOption({ label: 'Paginación E2E' });
    await expect(page.locator('tbody tr')).toHaveCount(12);

    const sizeSelect = page.getByLabel('Por página');

    await sizeSelect.selectOption('5');
    await expect(page.locator('tbody tr')).toHaveCount(5);
    await expect(page.locator('.page-indicator')).toHaveText('Página 1 de 3');

    await sizeSelect.selectOption('10');
    await expect(page.locator('tbody tr')).toHaveCount(10);
    await expect(page.locator('.page-indicator')).toHaveText('Página 1 de 2');

    await sizeSelect.selectOption('25');
    await expect(page.locator('tbody tr')).toHaveCount(12);
    await expect(page.locator('.page-indicator')).toHaveText('Página 1 de 1');
  });
});
