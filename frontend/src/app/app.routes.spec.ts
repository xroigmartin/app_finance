import { AccountsPage } from './pages/accounts/accounts';
import { BudgetsPage } from './pages/budgets/budgets';
import { CategoriesPage } from './pages/categories/categories';
import { DashboardPage } from './pages/dashboard/dashboard';
import { InvestmentsPage } from './pages/investments/investments';
import { TransactionsPage } from './pages/transactions/transactions';
import { routes } from './app.routes';

function findRoute(path: string) {
  const route = routes.find(r => r.path === path);
  if (!route) throw new Error(`No existe la ruta "${path}"`);
  return route;
}

describe('routes', () => {
  it('redirige la raíz al dashboard', () => {
    const root = findRoute('');
    expect(root.pathMatch).toBe('full');
    expect(root.redirectTo).toBe('dashboard');
  });

  it('redirige transfers a transactions (la gestión se hace desde Movimientos)', () => {
    expect(findRoute('transfers').redirectTo).toBe('transactions');
  });

  it('redirige cualquier ruta desconocida al dashboard', () => {
    expect(findRoute('**').redirectTo).toBe('dashboard');
  });

  it.each([
    ['dashboard', DashboardPage],
    ['transactions', TransactionsPage],
    ['investments', InvestmentsPage],
    ['budgets', BudgetsPage],
    ['accounts', AccountsPage],
    ['categories', CategoriesPage],
  ])('%s carga perezosamente el componente esperado', async (path, expected) => {
    const route = findRoute(path);
    const component = await route.loadComponent!();
    expect(component).toBe(expected);
  });
});
