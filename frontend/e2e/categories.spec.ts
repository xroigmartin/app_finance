import { expect, test } from '@playwright/test';

test.describe('Categorías', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/categories');
    await expect(page.getByRole('heading', { name: 'Categorías' })).toBeVisible();
  });

  function catDialog(page: import('@playwright/test').Page) {
    return page.locator('dialog[aria-labelledby="cat-form-title"]');
  }

  function ruleDialog(page: import('@playwright/test').Page) {
    return page.locator('dialog[aria-labelledby="rule-form-title"]');
  }

  test('alta, edición, subcategoría y borrado en cascada correcto', async ({ page }) => {
    // Alta de una categoría de gasto global.
    await page.getByRole('button', { name: '+ Nueva categoría' }).click();
    const form = catDialog(page);
    await form.getByLabel('Nombre').fill('Categoría E2E');
    await form.getByRole('button', { name: 'Guardar' }).click();
    await expect(form).toBeHidden();

    const gastos = page.locator('.columns .card', { hasText: 'Gastos' });
    const row = gastos.locator('li', { hasText: 'Categoría E2E' }).first();
    await expect(row).toBeVisible();

    // Edición.
    await row.getByRole('button', { name: 'Editar' }).click();
    await catDialog(page).getByLabel('Nombre').fill('Categoría E2E editada');
    await catDialog(page).getByRole('button', { name: 'Guardar' }).click();
    await expect(gastos.locator('li', { hasText: 'Categoría E2E editada' }).first()).toBeVisible();

    // Alta de subcategoría.
    await gastos.locator('li', { hasText: 'Categoría E2E editada' }).first()
      .getByRole('button', { name: '+ Sub' }).click();
    await catDialog(page).getByLabel('Nombre').fill('Sub E2E');
    await catDialog(page).getByRole('button', { name: 'Guardar' }).click();
    const subRow = gastos.locator('li.sub', { hasText: 'Sub E2E' });
    await expect(subRow).toBeVisible();

    // Borrar una categoría con subcategorías dispara DOS diálogos nativos en
    // cadena: primero el confirm() de "¿Eliminar...?" y, si se acepta, el
    // alert() de error 409 al fallar el borrado en el backend. Un manejador
    // persistente evita la condición de carrera de encadenar varios `once`.
    const dialogMessages: string[] = [];
    page.on('dialog', d => {
      dialogMessages.push(d.message());
      d.accept();
    });

    await gastos.locator('li', { hasText: 'Categoría E2E editada' }).first()
      .getByRole('button', { name: 'Eliminar' }).click();
    await expect(gastos.locator('li', { hasText: 'Categoría E2E editada' }).first()).toBeVisible();
    await expect.poll(() => dialogMessages.length).toBeGreaterThanOrEqual(2);
    expect(dialogMessages[1]).toContain('no se puede eliminar');

    // Se borra primero la subcategoría y luego la principal, sin problema.
    await subRow.getByRole('button', { name: 'Eliminar' }).click();
    await expect(subRow).toHaveCount(0);

    await gastos.locator('li', { hasText: 'Categoría E2E editada' }).first()
      .getByRole('button', { name: 'Eliminar' }).click();
    await expect(gastos.locator('li', { hasText: 'Categoría E2E editada' })).toHaveCount(0);
  });

  test('alta, edición y borrado de una regla de categorización', async ({ page }) => {
    await page.getByRole('button', { name: '+ Nueva regla' }).click();
    const form = ruleDialog(page);
    await form.getByLabel('Patrón').fill('SUPERMERCADOE2E');
    await form.getByLabel('Categoría').selectOption({ label: 'Alimentación (gasto) · Global' });
    await form.getByRole('button', { name: 'Guardar' }).click();
    await expect(form).toBeHidden();

    const row = page.locator('.rules-card tbody tr', { hasText: 'SUPERMERCADOE2E' });
    await expect(row).toBeVisible();

    await row.getByRole('button', { name: 'Editar' }).click();
    await ruleDialog(page).getByLabel('Patrón').fill('SUPERMERCADOE2E|MERCADONAE2E');
    await ruleDialog(page).getByRole('button', { name: 'Guardar' }).click();
    await expect(page.locator('.rules-card tbody tr', { hasText: 'SUPERMERCADOE2E|MERCADONAE2E' })).toBeVisible();

    page.once('dialog', d => d.accept());
    await page.locator('.rules-card tbody tr', { hasText: 'SUPERMERCADOE2E|MERCADONAE2E' })
      .getByRole('button', { name: 'Eliminar' }).click();
    await expect(page.locator('.rules-card tbody tr', { hasText: 'SUPERMERCADOE2E' })).toHaveCount(0);
  });
});
