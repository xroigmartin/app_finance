import { Routes } from '@angular/router';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
  {
    path: 'dashboard',
    loadComponent: () => import('./pages/dashboard/dashboard').then(m => m.DashboardPage)
  },
  {
    path: 'transactions',
    loadComponent: () => import('./pages/transactions/transactions').then(m => m.TransactionsPage)
  },
  // La gestión de transferencias se hace ahora desde Movimientos.
  { path: 'transfers', redirectTo: 'transactions' },
  { path: 'investments', pathMatch: 'full', redirectTo: 'investments/dashboard' },
  {
    path: 'investments/dashboard',
    loadComponent: () =>
      import('./pages/investments-dashboard/investments-dashboard').then(m => m.InvestmentsDashboardPage)
  },
  {
    path: 'investments/operations',
    loadComponent: () =>
      import('./pages/investments-operations/investments-operations').then(m => m.InvestmentsOperationsPage)
  },
  {
    path: 'budgets',
    loadComponent: () => import('./pages/budgets/budgets').then(m => m.BudgetsPage)
  },
  {
    path: 'accounts',
    loadComponent: () => import('./pages/accounts/accounts').then(m => m.AccountsPage)
  },
  {
    path: 'categories',
    loadComponent: () => import('./pages/categories/categories').then(m => m.CategoriesPage)
  },
  { path: '**', redirectTo: 'dashboard' }
];
