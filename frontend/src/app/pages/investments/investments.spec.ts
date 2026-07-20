import { ElementRef } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { ApiService } from '../../api.service';
import { ThemeService } from '../../theme.service';
import {
  InvestmentIncome, InvestmentPerformance, InvestmentSecurity, InvestmentTransactionView,
  Portfolio, PortfolioSummary, PositionView, ValuationPoint
} from '../../models';
import { InvestmentsPage } from './investments';

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

describe('InvestmentsPage', () => {
  afterEach(() => vi.restoreAllMocks());

  let api: {
    getSecurities: ReturnType<typeof vi.fn>;
    getPortfolios: ReturnType<typeof vi.fn>;
    getPortfolioSummary: ReturnType<typeof vi.fn>;
    getPositions: ReturnType<typeof vi.fn>;
    getValuationHistory: ReturnType<typeof vi.fn>;
    getInvestmentPerformance: ReturnType<typeof vi.fn>;
    getInvestmentTransactions: ReturnType<typeof vi.fn>;
    getInvestmentIncome: ReturnType<typeof vi.fn>;
    deleteInvestmentTransaction: ReturnType<typeof vi.fn>;
    createPortfolio: ReturnType<typeof vi.fn>;
  };

  const portfolio: Portfolio = { id: 1, name: 'Cartera E2E', baseCurrency: 'EUR' };
  const security: InvestmentSecurity = {
    id: 9, isin: 'US1', currency: 'EUR', name: 'Empresa', ticker: 'EMP', type: 'EQUITY', exchange: 'XETRA', figi: null,
  };
  const position: PositionView = {
    securityId: 9, isin: 'US1', name: 'Empresa', ticker: 'EMP', currency: 'EUR', quantity: 10,
    averageCost: 100, costBasis: 1000, marketPrice: 150, quoteDate: '2026-07-01', marketValue: 1500,
    latentPnl: 500, latentPnlPercent: 50, weight: 60, pricedAtCost: false,
  };
  const summary: PortfolioSummary = {
    portfolioId: 1, name: 'Cartera E2E', baseCurrency: 'EUR', totalValue: 2500, valuationDate: '2026-07-01',
    netContributions: 2000, latentPnl: 500, latentPnlPercent: 25, cashByCurrency: { EUR: 1000 },
    dividendsThisYear: 15,
  };
  const history: ValuationPoint[] = [
    { date: '2026-01-01', value: 2000, contributed: 2000 },
    { date: '2026-07-01', value: 2500, contributed: 2000 },
  ];
  const performance: InvestmentPerformance = {
    portfolioId: 1, baseCurrency: 'EUR', valuationDate: '2026-07-01', twrPercent: 25, xirrPercent: 20,
    positions: [{ securityId: 9, name: 'Empresa', twrPercent: 25, xirrPercent: 20 }],
  };
  const transactions: InvestmentTransactionView[] = [];
  const income: InvestmentIncome = {
    portfolioId: 1, baseCurrency: 'EUR',
    incomes: [
      { securityId: 9, name: 'Empresa', month: '2026-03', gross: 10, withheld: 1.5, net: 8.5 },
      { securityId: 9, name: 'Empresa', month: '2025-03', gross: 5, withheld: 0.75, net: 4.25 },
    ],
    fees: [{ month: '2026-03', amount: 2 }, { month: '2025-03', amount: 1 }],
    taxes: [{ month: '2026-03', amount: 0.5 }, { month: '2025-03', amount: 0.25 }],
  };

  function create(overrides: Partial<typeof api> = {}, portfolios: Portfolio[] = [portfolio]): InvestmentsPage {
    api = {
      getSecurities: vi.fn().mockReturnValue(of([security])),
      getPortfolios: vi.fn().mockReturnValue(of(portfolios)),
      getPortfolioSummary: vi.fn().mockReturnValue(of(summary)),
      getPositions: vi.fn().mockReturnValue(of([position])),
      getValuationHistory: vi.fn().mockReturnValue(of(history)),
      getInvestmentPerformance: vi.fn().mockReturnValue(of(performance)),
      getInvestmentTransactions: vi.fn().mockReturnValue(of(transactions)),
      getInvestmentIncome: vi.fn().mockReturnValue(of(income)),
      deleteInvestmentTransaction: vi.fn().mockReturnValue(of(undefined)),
      createPortfolio: vi.fn().mockReturnValue(of({ ...portfolio, id: 2 })),
      ...overrides,
    };
    TestBed.configureTestingModule({ providers: [{ provide: ApiService, useValue: api }] });
    const page = TestBed.createComponent(InvestmentsPage).componentInstance;
    return page;
  }

  function fakeCanvas(): ElementRef<HTMLCanvasElement> {
    return { nativeElement: {} } as ElementRef<HTMLCanvasElement>;
  }

  function withCanvases(page: InvestmentsPage): InvestmentsPage {
    page.allocationCanvas = fakeCanvas();
    page.evolutionCanvas = fakeCanvas();
    page.pnlCanvas = fakeCanvas();
    page.performanceCanvas = fakeCanvas();
    page.dividendCanvas = fakeCanvas();
    return page;
  }

  it('ngOnInit carga valores, selecciona la primera cartera y la información asociada', () => {
    const page = create();
    page.ngOnInit();
    expect(page.securities).toEqual([security]);
    expect(page.portfolioId).toBe(1);
    expect(page.loaded).toBe(true);
    expect(page.summary).toEqual(summary);
    expect(page.positions).toEqual([position]);
    expect(page.income).toEqual(income);
  });

  it('sin carteras, portfolioId queda a null y load() resetea todo', () => {
    const page = create({}, []);
    page.ngOnInit();
    expect(page.portfolioId).toBeNull();
    expect(page.summary).toBeNull();
    expect(page.positions).toEqual([]);
    expect(page.income).toBeNull();
  });

  describe('getters', () => {
    it('portfolio resuelve la cartera seleccionada', () => {
      const page = create();
      page.ngOnInit();
      expect(page.portfolio).toEqual(portfolio);
    });

    it('baseCurrency prioriza el summary, luego la cartera, luego EUR', () => {
      const page = create();
      page.ngOnInit();
      expect(page.baseCurrency).toBe('EUR');
      page.summary = { ...summary, baseCurrency: 'USD' };
      expect(page.baseCurrency).toBe('USD');
      page.summary = null;
      expect(page.baseCurrency).toBe(portfolio.baseCurrency);
    });

    it('cashEntries convierte el mapa de efectivo por divisa en una lista', () => {
      const page = create();
      page.ngOnInit();
      expect(page.cashEntries).toEqual([{ currency: 'EUR', amount: 1000 }]);
    });

    it('cashEntries está vacío sin summary', () => {
      const page = create({}, []);
      page.ngOnInit();
      expect(page.cashEntries).toEqual([]);
    });

    it('perfOf busca la rentabilidad de una posición', () => {
      const page = create();
      page.ngOnInit();
      expect(page.perfOf(9)?.twrPercent).toBe(25);
      expect(page.perfOf(999)).toBeNull();
    });
  });

  it('onPortfolioChange recarga', () => {
    const page = create();
    page.ngOnInit();
    api.getPortfolioSummary.mockClear();
    page.onPortfolioChange();
    expect(api.getPortfolioSummary).toHaveBeenCalledWith(1);
  });

  describe('operaciones', () => {
    it('loadTransactions construye el filtro con lo que hay puesto', () => {
      const page = create();
      page.ngOnInit();
      api.getInvestmentTransactions.mockClear();
      page.filterType = 'BUY';
      page.filterFrom = '2026-01-01';
      page.filterTo = '2026-12-31';
      page.filterSecurityId = 9;
      page.loadTransactions();
      expect(api.getInvestmentTransactions).toHaveBeenCalledWith(1, {
        type: 'BUY', from: '2026-01-01', to: '2026-12-31', securityId: 9,
      });
    });

    it('loadTransactions no llama a la API sin cartera seleccionada', () => {
      const page = create({}, []);
      page.ngOnInit();
      api.getInvestmentTransactions.mockClear();
      page.loadTransactions();
      expect(api.getInvestmentTransactions).not.toHaveBeenCalled();
    });

    it('editTransaction delega en el diálogo', () => {
      const page = create();
      page.ngOnInit();
      const edit = vi.fn();
      page.txDialog = { edit } as unknown as InvestmentsPage['txDialog'];
      const tx = { id: 1 } as InvestmentTransactionView;
      page.editTransaction(tx);
      expect(edit).toHaveBeenCalledWith(tx);
    });

    it('deleteTransaction pide confirmación y borra', () => {
      vi.spyOn(window, 'confirm').mockReturnValue(true);
      const page = create();
      page.ngOnInit();
      const tx: InvestmentTransactionView = {
        id: 5, type: 'BUY', tradeDate: '2026-01-01', securityId: 9, securityName: 'Empresa',
        quantity: 1, price: 100, amount: -100, currency: 'EUR', counterAmount: null,
      } as InvestmentTransactionView;
      page.deleteTransaction(tx);
      expect(api.deleteInvestmentTransaction).toHaveBeenCalledWith(5);
    });

    it('deleteTransaction no borra si no se confirma', () => {
      vi.spyOn(window, 'confirm').mockReturnValue(false);
      const page = create();
      page.ngOnInit();
      page.deleteTransaction({ id: 5, type: 'SELL' } as InvestmentTransactionView);
      expect(api.deleteInvestmentTransaction).not.toHaveBeenCalled();
    });
  });

  describe('dividendos', () => {
    it('loadIncome calcula los años disponibles, orden descendente', () => {
      const page = create();
      page.ngOnInit();
      expect(page.incomeYears).toEqual([2026, 2025]);
    });

    it('si el año seleccionado deja de existir, cae al más reciente disponible', () => {
      const page = create();
      page.incomeYear = 1999;
      page.ngOnInit();
      expect(page.incomeYear).toBe(2026);
    });

    it('incomeRows agrega por instrumento dentro del año seleccionado', () => {
      const page = create();
      page.ngOnInit();
      page.incomeYear = 2026;
      expect(page.incomeRows).toEqual([{ name: 'Empresa', gross: 10, withheld: 1.5, net: 8.5 }]);
    });

    it('incomeRows con "Todo" agrega todos los años', () => {
      const page = create();
      page.ngOnInit();
      page.incomeYear = 'all';
      expect(page.incomeRows[0].gross).toBe(15);
    });

    it('incomeTotals suma las filas', () => {
      const page = create();
      page.ngOnInit();
      page.incomeYear = 'all';
      expect(page.incomeTotals).toEqual({ gross: 15, withheld: 2.25, net: 12.75 });
    });

    it('feesTotal/taxesTotal filtran por el año seleccionado', () => {
      const page = create();
      page.ngOnInit();
      page.incomeYear = 2026;
      expect(page.feesTotal).toBe(2);
      expect(page.taxesTotal).toBe(0.5);
      page.incomeYear = 'all';
      expect(page.feesTotal).toBe(3);
    });
  });

  describe('alta de cartera', () => {
    it('startCreate resetea el formulario', () => {
      const page = create();
      page.createError = 'algo';
      page.startCreate();
      expect(page.creating).toBe(true);
      expect(page.newName).toBe('');
      expect(page.newCurrency).toBe('EUR');
      expect(page.createError).toBe('');
    });

    it('cancelCreate cierra el formulario', () => {
      const page = create();
      page.creating = true;
      page.cancelCreate();
      expect(page.creating).toBe(false);
    });

    it('createPortfolio no hace nada sin nombre o divisa', () => {
      const page = create();
      page.newName = '  ';
      page.newCurrency = 'EUR';
      page.createPortfolio();
      expect(api.createPortfolio).not.toHaveBeenCalled();
    });

    it('createPortfolio crea, la añade y la selecciona', () => {
      const page = create();
      page.ngOnInit();
      page.newName = 'Nueva';
      page.newCurrency = 'usd';
      page.createPortfolio();
      expect(api.createPortfolio).toHaveBeenCalledWith({ name: 'Nueva', baseCurrency: 'USD' });
      expect(page.portfolioId).toBe(2);
      expect(page.creating).toBe(false);
    });

    it('en error, fija createError con el detalle de la API', () => {
      const page = create({
        createPortfolio: vi.fn().mockReturnValue(throwError(() => ({ error: { detail: 'boom' } }))),
      });
      page.newName = 'Nueva';
      page.createPortfolio();
      expect(page.createError).toBe('boom');
    });
  });

  describe('ayudas visuales de posición', () => {
    it('tickerLabel usa el ticker o el nombre, en mayúsculas y máximo 4', () => {
      const page = create();
      expect(page.tickerLabel(position)).toBe('EMP');
      expect(page.tickerLabel({ ...position, ticker: null, name: 'empresa' })).toBe('EMPR');
    });

    it('tickerColor cicla la paleta y tickerSoft le añade alfa', () => {
      const page = create();
      expect(page.tickerColor(0)).toBe('#2563EB');
      expect(page.tickerSoft(0)).toBe('#2563EB24');
    });
  });

  describe('gráficos', () => {
    it('sin datos (sin cartera), solo dibuja evolución y P&L (sin guarda de null)', () => {
      const page = create({}, []);
      withCanvases(page);
      page.ngOnInit();
      page.ngAfterViewInit();
      const charts = (page as unknown as { charts: unknown[] }).charts;
      expect(charts.length).toBe(2);
    });

    it('el donut de asignación reparte posiciones abiertas + efectivo', () => {
      const page = create();
      withCanvases(page);
      page.ngOnInit();
      page.ngAfterViewInit();
      const charts = (page as unknown as {
        charts: { config: { type: string; data: { labels: string[]; datasets: { data: number[] }[] } } }[]
      }).charts;
      const allocation = charts[0];
      expect(allocation.config.type).toBe('doughnut');
      expect(allocation.config.data.labels).toEqual(['Empresa', 'Efectivo']);
      expect(allocation.config.data.datasets[0].data).toEqual([1500, 1000]); // 2500 total - 1500 posiciones
    });

    it('evolución expone valor y aportado', () => {
      const page = create();
      withCanvases(page);
      page.ngOnInit();
      page.ngAfterViewInit();
      const charts = (page as unknown as {
        charts: { config: { data: { labels: string[]; datasets: { label: string; data: number[] }[] } } }[]
      }).charts;
      const evolution = charts[1];
      expect(evolution.config.data.labels).toEqual(['2026-01-01', '2026-07-01']);
      expect(evolution.config.data.datasets[0]).toEqual(expect.objectContaining({ label: 'Valor', data: [2000, 2500] }));
      expect(evolution.config.data.datasets[1]).toEqual(expect.objectContaining({ label: 'Aportado', data: [2000, 2000] }));
    });

    it('P&L colorea en positivo/negativo y ordena de mejor a peor', () => {
      const page = create({ getPositions: vi.fn().mockReturnValue(of([position, { ...position, securityId: 10, name: 'Perdedora', latentPnl: -200 }])) });
      withCanvases(page);
      page.ngOnInit();
      page.ngAfterViewInit();
      const theme = TestBed.inject(ThemeService);
      const charts = (page as unknown as {
        charts: { config: { data: { labels: string[]; datasets: { data: number[]; backgroundColor: string[] }[] } } }[]
      }).charts;
      const pnl = charts[2];
      expect(pnl.config.data.labels).toEqual(['Empresa', 'Perdedora']);
      expect(pnl.config.data.datasets[0].backgroundColor).toEqual([theme.chartPos(), theme.chartNeg()]);
    });

    it('rentabilidad por posición ordena por XIRR descendente', () => {
      const page = create({
        getInvestmentPerformance: vi.fn().mockReturnValue(of({
          ...performance,
          positions: [
            { securityId: 9, name: 'Baja', twrPercent: 5, xirrPercent: 5 },
            { securityId: 10, name: 'Alta', twrPercent: 30, xirrPercent: 30 },
          ],
        })),
      });
      withCanvases(page);
      page.ngOnInit();
      page.ngAfterViewInit();
      const charts = (page as unknown as {
        charts: { config: { data: { labels: string[] } } }[]
      }).charts;
      const perfChart = charts[3];
      expect(perfChart.config.data.labels).toEqual(['Alta', 'Baja']);
    });

    it('dividendos agrupa por instrumento y por mes del año seleccionado', () => {
      const page = create();
      withCanvases(page);
      page.ngOnInit();
      page.incomeYear = 2026;
      page.ngAfterViewInit();
      const charts = (page as unknown as {
        charts: { config: { data: { labels: string[]; datasets: { label: string; data: number[] }[] } } }[]
      }).charts;
      const dividendChart = charts[4];
      expect(dividendChart.config.data.labels.length).toBe(12);
      expect(dividendChart.config.data.datasets[0].label).toBe('Empresa');
      expect(dividendChart.config.data.datasets[0].data[2]).toBe(10); // marzo, índice 2
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
