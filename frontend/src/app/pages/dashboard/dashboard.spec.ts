import { ElementRef } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { ApiService } from '../../api.service';
import { ThemeService } from '../../theme.service';
import {
  Account, AccountComparison, BalancePoint, BudgetStatus, CategoryAmount, InvestmentsSummary,
  MonthlyPoint, Summary, Transaction
} from '../../models';
import { DashboardPage } from './dashboard';

vi.mock('chart.js', () => {
  class MockChart {
    static register = vi.fn();
    static defaults: Record<string, unknown> = { font: {} };
    destroy = vi.fn();
    canvas: unknown;
    config: unknown;
    constructor(canvas: unknown, config: unknown) {
      this.canvas = canvas;
      this.config = config;
    }
  }
  return { Chart: MockChart, registerables: [] };
});

describe('DashboardPage', () => {
  let api: {
    getAccounts: ReturnType<typeof vi.fn>;
    getRecentTransactions: ReturnType<typeof vi.fn>;
    getInvestmentsSummary: ReturnType<typeof vi.fn>;
    getSummary: ReturnType<typeof vi.fn>;
    getBudgetStatus: ReturnType<typeof vi.fn>;
    getMonthly: ReturnType<typeof vi.fn>;
    getExpensesByCategory: ReturnType<typeof vi.fn>;
    getIncomeByCategory: ReturnType<typeof vi.fn>;
    getMonthlyBalance: ReturnType<typeof vi.fn>;
    getAccountComparison: ReturnType<typeof vi.fn>;
  };

  const account: Account = { id: 1, name: 'Corriente', type: 'Banco', initialBalance: 0 };
  const monthly: MonthlyPoint[] = [
    { month: '2026-06', income: 2000, expense: 1500 },
    { month: '2026-07', income: 2500, expense: 1800 },
  ];
  const expenseCat: CategoryAmount[] = [{ category: 'Alimentación', color: '#a00', amount: 300 }];
  const incomeCat: CategoryAmount[] = [{ category: 'Nómina', color: '#0a0', amount: 2500 }];
  const balance: BalancePoint[] = [{ month: '2026-06', balance: 1000 }, { month: '2026-07', balance: 1700 }];
  const comparison: AccountComparison = {
    months: ['2026-06', '2026-07'],
    accounts: [{ accountId: 1, name: 'Corriente', income: [2000, 2500], expense: [1500, 1800] }],
  };
  const investmentsSummary: InvestmentsSummary = { totalValue: 5000, valuationDate: '2026-07-01', portfolios: [] };
  const budgetStatus: BudgetStatus[] = [
    { budgetId: 1, accountId: 1, account: 'Corriente', categoryId: 2, category: 'Alimentación', color: '#a00', budget: 400, spent: 300, remaining: 100 },
  ];

  function create(overrides: Partial<typeof api> = {}): DashboardPage {
    api = {
      getAccounts: vi.fn().mockReturnValue(of([account])),
      getRecentTransactions: vi.fn().mockReturnValue(of([] as Transaction[])),
      getInvestmentsSummary: vi.fn().mockReturnValue(of(investmentsSummary)),
      getSummary: vi.fn().mockReturnValue(of({} as Summary)),
      getBudgetStatus: vi.fn().mockReturnValue(of(budgetStatus)),
      getMonthly: vi.fn().mockReturnValue(of(monthly)),
      getExpensesByCategory: vi.fn().mockReturnValue(of(expenseCat)),
      getIncomeByCategory: vi.fn().mockReturnValue(of(incomeCat)),
      getMonthlyBalance: vi.fn().mockReturnValue(of(balance)),
      getAccountComparison: vi.fn().mockReturnValue(of(comparison)),
      ...overrides,
    };
    TestBed.configureTestingModule({ providers: [{ provide: ApiService, useValue: api }] });
    const page = TestBed.createComponent(DashboardPage).componentInstance;
    return page;
  }

  function fakeCanvas(): ElementRef<HTMLCanvasElement> {
    return { nativeElement: {} } as ElementRef<HTMLCanvasElement>;
  }

  function withCanvases(page: DashboardPage): DashboardPage {
    page.monthlyCanvas = fakeCanvas();
    page.categoryCanvas = fakeCanvas();
    page.incomeCategoryCanvas = fakeCanvas();
    page.savingsCanvas = fakeCanvas();
    page.balanceCanvas = fakeCanvas();
    page.incomeCompareCanvas = fakeCanvas();
    page.expenseCompareCanvas = fakeCanvas();
    return page;
  }

  it('ngOnInit carga cuentas, recientes, resumen de inversión y el resto', () => {
    const page = create();
    page.ngOnInit();
    expect(page.accounts).toEqual([account]);
    expect(page.investments).toEqual(investmentsSummary);
    expect(page.budgets).toEqual(budgetStatus);
  });

  it('degrada a error si falla el resumen de inversión, sin tumbar el resto', () => {
    const page = create({ getInvestmentsSummary: vi.fn().mockReturnValue(throwError(() => new Error('down'))) });
    page.ngOnInit();
    expect(page.investmentsError).toBe(true);
    expect(page.accounts).toEqual([account]);
  });

  describe('getters', () => {
    it('accountLabel resuelve el nombre o "Todas las cuentas"', () => {
      const page = create();
      page.ngOnInit();
      expect(page.accountLabel).toBe('Todas las cuentas');
      page.accountId = 1;
      expect(page.accountLabel).toBe('Corriente');
    });

    it('monthLabel combina el nombre del mes y el año', () => {
      const page = create();
      page.year = 2026;
      page.month = 7;
      expect(page.monthLabel).toBe('Julio 2026');
    });

    it('isCurrentMonth compara con la fecha real', () => {
      const page = create();
      const now = new Date();
      page.year = now.getFullYear();
      page.month = now.getMonth() + 1;
      expect(page.isCurrentMonth).toBe(true);
      page.year = now.getFullYear() - 1;
      expect(page.isCurrentMonth).toBe(false);
    });
  });

  describe('navegación de mes', () => {
    it('prevMonth retrocede y cruza el año en enero', () => {
      const page = create();
      page.year = 2026;
      page.month = 1;
      page.prevMonth();
      expect(page.month).toBe(12);
      expect(page.year).toBe(2025);
    });

    it('nextMonth avanza y cruza el año en diciembre', () => {
      const page = create();
      page.year = 2026;
      page.month = 12;
      page.nextMonth();
      expect(page.month).toBe(1);
      expect(page.year).toBe(2027);
    });

    it('goToCurrentMonth vuelve al mes y año actuales', () => {
      const page = create();
      page.year = 2020;
      page.month = 1;
      page.goToCurrentMonth();
      const now = new Date();
      expect(page.year).toBe(now.getFullYear());
      expect(page.month).toBe(now.getMonth() + 1);
    });

    it('onAccountChange recarga', () => {
      const page = create();
      api.getSummary.mockClear();
      page.accountId = 1;
      page.onAccountChange();
      expect(api.getSummary).toHaveBeenCalledWith(page.year, page.month, 1);
    });
  });

  describe('presupuestos', () => {
    it('percent limita a 100 y es 0 sin presupuesto', () => {
      const page = create();
      expect(page.percent({ ...budgetStatus[0], budget: 0 })).toBe(0);
      expect(page.percent({ ...budgetStatus[0], budget: 100, spent: 50 })).toBe(50);
      expect(page.percent({ ...budgetStatus[0], budget: 100, spent: 300 })).toBe(100);
    });

    it('overBudget compara gastado con presupuestado', () => {
      const page = create();
      expect(page.overBudget({ ...budgetStatus[0], budget: 100, spent: 50 })).toBe(false);
      expect(page.overBudget({ ...budgetStatus[0], budget: 100, spent: 150 })).toBe(true);
    });
  });

  it('soft añade alfa a un color hex válido y deja tal cual el resto', () => {
    const page = create();
    expect(page.soft('#112233')).toBe('#11223324');
    expect(page.soft('var(--accent)')).toBe('var(--accent)');
  });

  describe('gráficos', () => {
    it('sin comparativa, no dibuja los gráficos de comparación por cuenta', () => {
      const page = create({ getAccountComparison: vi.fn().mockReturnValue(of(null)) });
      withCanvases(page);
      page.ngOnInit();
      page.ngAfterViewInit();
      const charts = (page as unknown as { charts: { config: { type?: string } }[] }).charts;
      expect(charts.length).toBe(5);
    });

    it('dibuja evolución mensual con ingresos/gastos y los colores del tema', () => {
      const page = create();
      withCanvases(page);
      page.ngOnInit();
      page.ngAfterViewInit();
      const theme = TestBed.inject(ThemeService);
      const charts = (page as unknown as {
        charts: { config: { type: string; data: { labels: string[]; datasets: { label: string; data: number[]; backgroundColor: string }[] } } }[]
      }).charts;
      const monthlyChart = charts[0];
      expect(monthlyChart.config.type).toBe('bar');
      expect(monthlyChart.config.data.labels).toEqual(['2026-06', '2026-07']);
      expect(monthlyChart.config.data.datasets[0]).toEqual(
        expect.objectContaining({ label: 'Ingresos', data: [2000, 2500], backgroundColor: theme.chartPos() }),
      );
      expect(monthlyChart.config.data.datasets[1]).toEqual(
        expect.objectContaining({ label: 'Gastos', data: [1500, 1800], backgroundColor: theme.chartNeg() }),
      );
    });

    it('los donuts de categoría usan las etiquetas/importes/colores de cada categoría', () => {
      const page = create();
      withCanvases(page);
      page.ngOnInit();
      page.ngAfterViewInit();
      const charts = (page as unknown as {
        charts: { config: { type: string; data: { labels: string[]; datasets: { data: number[]; backgroundColor: string[] }[] } } }[]
      }).charts;
      const expenseDoughnut = charts[1];
      expect(expenseDoughnut.config.type).toBe('doughnut');
      expect(expenseDoughnut.config.data.labels).toEqual(['Alimentación']);
      expect(expenseDoughnut.config.data.datasets[0].data).toEqual([300]);
      expect(expenseDoughnut.config.data.datasets[0].backgroundColor).toEqual(['#a00']);
    });

    it('el gráfico de ahorro calcula el acumulado mes a mes y colorea por signo', () => {
      const page = create();
      withCanvases(page);
      page.ngOnInit();
      page.ngAfterViewInit();
      const theme = TestBed.inject(ThemeService);
      const charts = (page as unknown as {
        charts: {
          config: {
            data: {
              datasets: (
                { type: string; data: number[]; backgroundColor?: string[] }
              )[];
            };
          };
        }[]
      }).charts;
      const savingsChart = charts[3];
      const savingsDataset = savingsChart.config.data.datasets[0];
      const cumulativeDataset = savingsChart.config.data.datasets[1];
      expect(savingsDataset.data).toEqual([500, 700]); // 2000-1500, 2500-1800
      expect(savingsDataset.backgroundColor).toEqual([theme.chartPos(), theme.chartPos()]);
      expect(cumulativeDataset.data).toEqual([500, 1200]);
    });

    it('con comparativa, dibuja un gráfico por cuenta con colores cíclicos', () => {
      const page = create();
      withCanvases(page);
      page.ngOnInit();
      page.ngAfterViewInit();
      const charts = (page as unknown as {
        charts: { config: { data: { labels: string[]; datasets: { label: string; borderColor: string }[] } } }[]
      }).charts;
      expect(charts.length).toBe(7);
      const incomeCompareChart = charts[5];
      expect(incomeCompareChart.config.data.labels).toEqual(['2026-06', '2026-07']);
      expect(incomeCompareChart.config.data.datasets[0]).toEqual(
        expect.objectContaining({ label: 'Corriente', borderColor: '#2563EB' }),
      );
    });

    it('renderCharts no hace nada hasta que la vista está lista', () => {
      const page = create();
      withCanvases(page);
      page.ngOnInit();
      const charts = (page as unknown as { charts: unknown[] }).charts;
      expect(charts.length).toBe(0);
    });

    it('ngOnDestroy destruye todos los gráficos', () => {
      const page = create();
      withCanvases(page);
      page.ngOnInit();
      page.ngAfterViewInit();
      const charts = (page as unknown as { charts: { destroy: ReturnType<typeof vi.fn> }[] }).charts;
      page.ngOnDestroy();
      charts.forEach(c => expect(c.destroy).toHaveBeenCalled());
    });
  });
});
