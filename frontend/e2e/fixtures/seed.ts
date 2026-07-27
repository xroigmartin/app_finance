import { backendUrl } from '../env';

/**
 * POST helper: falla con el body de error si la API devuelve algo != 2xx.
 * Exportado para que los specs puedan sembrar datos ad hoc propios (p. ej. un
 * volumen de filas que la siembra fija global no necesita) sin tocarla.
 */
export async function post<T>(path: string, body: unknown): Promise<T> {
  const res = await fetch(`${backendUrl}${path}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
  if (!res.ok) {
    throw new Error(`POST ${path} -> ${res.status}: ${await res.text()}`);
  }
  return res.json() as Promise<T>;
}

export async function get<T>(path: string): Promise<T> {
  const res = await fetch(`${backendUrl}${path}`);
  if (!res.ok) {
    throw new Error(`GET ${path} -> ${res.status}: ${await res.text()}`);
  }
  return res.json() as Promise<T>;
}

/**
 * Siembra un dataset fijo y conocido contra el backend real (RN de dominio
 * incluidas: no INSERT SQL) para que los E2E tengan datos deterministas.
 * Se ejecuta una vez en el globalSetup de Playwright, contra la BD e2e recién
 * reseteada (categorías por defecto ya sembradas por el DataSeeder del backend).
 */
export async function seed(): Promise<void> {
  const bank = await post<{ id: number }>('/api/accounts', {
    name: 'Cuenta Corriente E2E',
    type: 'Banco',
    initialBalance: 1000,
  });
  await post('/api/accounts', {
    name: 'Ahorro E2E',
    type: 'Efectivo',
    initialBalance: 500,
  });

  const categories = await get<{ id: number; name: string }[]>('/api/categories');
  const nomina = categories.find(c => c.name === 'Nómina')!;
  const alimentacion = categories.find(c => c.name === 'Alimentación')!;
  const vivienda = categories.find(c => c.name === 'Vivienda')!;

  const today = new Date();
  const month = (offset: number): string => {
    const d = new Date(today.getFullYear(), today.getMonth() + offset, 1);
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;
  };

  await post('/api/transactions', {
    date: `${month(0)}-05`,
    amount: 2500,
    description: 'Nómina julio',
    type: 'INCOME',
    accountId: bank.id,
    categoryId: nomina.id,
  });
  await post('/api/transactions', {
    date: `${month(0)}-01`,
    amount: 900,
    description: 'Alquiler',
    type: 'EXPENSE',
    accountId: bank.id,
    categoryId: vivienda.id,
  });
  await post('/api/transactions', {
    date: `${month(0)}-10`,
    amount: 350,
    description: 'Supermercado',
    type: 'EXPENSE',
    accountId: bank.id,
    categoryId: alimentacion.id,
  });
  await post('/api/transactions', {
    date: `${month(-1)}-05`,
    amount: 2500,
    description: 'Nómina mes anterior',
    type: 'INCOME',
    accountId: bank.id,
    categoryId: nomina.id,
  });
  await post('/api/transactions', {
    date: `${month(-1)}-12`,
    amount: 300,
    description: 'Supermercado mes anterior',
    type: 'EXPENSE',
    accountId: bank.id,
    categoryId: alimentacion.id,
  });

  const portfolio = await post<{ id: number }>('/api/investments/portfolios', {
    name: 'Cartera E2E',
    baseCurrency: 'EUR',
  });
  const security = await post<{ id: number }>('/api/investments/securities', {
    isin: 'US0000000E2E',
    currency: 'EUR',
    name: 'Empresa de Pruebas E2E',
    ticker: 'E2E',
    type: 'EQUITY',
    exchange: 'XETRA',
    figi: null,
  });
  await post(`/api/investments/portfolios/${portfolio.id}/transactions`, {
    type: 'DEPOSIT',
    tradeDate: '2026-01-15',
    amount: 5000,
    currency: 'EUR',
  });
  await post(`/api/investments/portfolios/${portfolio.id}/transactions`, {
    type: 'BUY',
    tradeDate: '2026-02-01',
    securityId: security.id,
    quantity: 10,
    price: 150,
    amount: -1500,
    currency: 'EUR',
  });
  await post(`/api/investments/portfolios/${portfolio.id}/transactions`, {
    type: 'DIVIDEND',
    tradeDate: '2026-03-15',
    securityId: security.id,
    amount: 20,
    currency: 'EUR',
  });
}
