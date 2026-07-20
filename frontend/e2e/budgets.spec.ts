import { expect, test } from '@playwright/test';

test.describe('Presupuestos', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/budgets');
    await expect(page.getByRole('heading', { name: 'Presupuestos' })).toBeVisible();
    await page.locator('.account-select').getByLabel('Cuenta').selectOption({ label: 'Cuenta Corriente E2E' });
  });

  test('la matriz muestra el gasto real de los movimientos sembrados', async ({ page }) => {
    const row = page.locator('table.annual tr', { hasText: 'Alimentación' }).first();
    // allTextContents() no reintenta: esperar explícitamente a que la carga real termine.
    await expect.poll(async () => {
      const cells = await row.locator('td.real').allTextContents();
      return cells.some(t => t.trim() !== '');
    }).toBe(true);
  });

  test('edita, actualiza y borra el presupuesto de una celda', async ({ page }) => {
    const row = page.locator('table.annual tr', { hasText: 'Alimentación' }).first();
    const cell = row.locator('input.cell-input').first();

    await cell.fill('500');
    await cell.press('Tab');
    await expect(page.locator('table.annual tr', { hasText: 'Alimentación' }).first()
      .locator('input.cell-input').first()).toHaveValue('500');

    // Persiste de verdad: recargando la página se lee del backend, no de estado local.
    await page.reload();
    await page.locator('.account-select').getByLabel('Cuenta').selectOption({ label: 'Cuenta Corriente E2E' });
    const reloadedCell = page.locator('table.annual tr', { hasText: 'Alimentación' }).first()
      .locator('input.cell-input').first();
    await expect(reloadedCell).toHaveValue('500');

    await reloadedCell.fill('');
    await reloadedCell.press('Tab');
    await expect(page.locator('table.annual tr', { hasText: 'Alimentación' }).first()
      .locator('input.cell-input').first()).toHaveValue('');
  });

  test('sin cuenta seleccionada, la matriz es de solo lectura', async ({ page }) => {
    await page.locator('.account-select').getByLabel('Cuenta').selectOption({ label: 'Todas las cuentas' });
    await expect(page.locator('table.annual input.cell-input')).toHaveCount(0);
  });
});
