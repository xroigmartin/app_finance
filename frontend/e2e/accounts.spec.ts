import { expect, test } from '@playwright/test';

test.describe('Cuentas', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/accounts');
    await expect(page.getByRole('heading', { name: 'Cuentas', exact: true })).toBeVisible();
  });

  function accountDialog(page: import('@playwright/test').Page) {
    return page.locator('dialog[aria-labelledby="account-form-title"]');
  }

  test('muestra el hero de patrimonio con las cuentas sembradas', async ({ page }) => {
    await expect(page.locator('.patrimony .figure')).toBeVisible();
    await expect(page.getByText('Cuenta Corriente E2E')).toBeVisible();
    await expect(page.getByText('Ahorro E2E')).toBeVisible();
  });

  test('alta, edición y borrado de una cuenta', async ({ page }) => {
    await page.getByRole('button', { name: '+ Nueva cuenta' }).click();
    const form = accountDialog(page);
    await form.getByLabel('Nombre').fill('Cuenta E2E Nueva');
    await form.getByLabel('Tipo').selectOption('Efectivo');
    await form.getByLabel('Saldo inicial').fill('1000');
    await form.getByRole('button', { name: 'Guardar' }).click();
    await expect(form).toBeHidden();

    const card = page.locator('.account-card', { hasText: 'Cuenta E2E Nueva' });
    await expect(card).toBeVisible();
    await expect(card).toContainText('1.000,00');

    await card.click();
    await accountDialog(page).getByLabel('Nombre').fill('Cuenta E2E Editada');
    await accountDialog(page).getByRole('button', { name: 'Guardar' }).click();
    const editedCard = page.locator('.account-card', { hasText: 'Cuenta E2E Editada' });
    await expect(editedCard).toBeVisible();

    page.once('dialog', d => d.accept());
    await editedCard.getByRole('button', { name: 'Eliminar' }).click();
    await expect(page.locator('.account-card', { hasText: 'Cuenta E2E Editada' })).toHaveCount(0);
  });
});
