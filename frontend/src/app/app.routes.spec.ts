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

  it('redirige investments al panel general de inversión', () => {
    const investments = findRoute('investments');
    expect(investments.pathMatch).toBe('full');
    expect(investments.redirectTo).toBe('investments/dashboard');
  });

  it('redirige cualquier ruta desconocida al dashboard', () => {
    expect(findRoute('**').redirectTo).toBe('dashboard');
  });

  // No se invoca route.loadComponent() ni se importan aquí los componentes de
  // página: hacerlo junto con los imports estáticos que ya usa cada
  // *.spec.ts de página (dashboard.spec.ts, investments.spec.ts...) provoca,
  // solo con la suite completa, una doble instanciación del módulo importado
  // a la vez de forma estática y dinámica — el bundler experimental de
  // Vitest (@angular/build:unit-test) deja alguna exportación de models.ts
  // como undefined en una de las dos copias (visto con
  // INVESTMENT_TYPE_LABELS). Confirmado que no es un bug de la app: `ng
  // build`/`ng serve` funcionan bien y navegar entre páginas reales también.
  // Qué carga cada ruta lo verifica el smoke E2E (CP2/CP8) navegando de
  // verdad; aquí solo se comprueba la forma de la configuración de rutas.
  it.each(['dashboard', 'transactions', 'investments/dashboard', 'investments/operations', 'budgets', 'accounts', 'categories'])(
    '%s es una ruta con carga perezosa',
    path => {
      const route = findRoute(path);
      expect(typeof route.loadComponent).toBe('function');
    },
  );
});
