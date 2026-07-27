import { ElementRef } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { ApiService } from '../../api.service';
import { ThemeService } from '../../theme.service';
import { InvestmentContextService } from '../../investment-context.service';
import {
  InvestmentPerformance, InvestmentSecurity, Portfolio, PortfolioSummary, PositionView, ValuationPoint
} from '../../models';
import { InvestmentsDashboardPage } from './investments-dashboard';

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

describe('InvestmentsDashboardPage', () => {
  afterEach(() => vi.restoreAllMocks());

  let api: {
    getSecurities: ReturnType<typeof vi.fn>;
    getPortfolios: ReturnType<typeof vi.fn>;
    getPortfolioSummary: ReturnType<typeof vi.fn>;
    getPositions: ReturnType<typeof vi.fn>;
    getValuationHistory: ReturnType<typeof vi.fn>;
    getInvestmentPerformance: ReturnType<typeof vi.fn>;
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

  function create(overrides: Partial<typeof api> = {}, portfolios: Portfolio[] = [portfolio]):
    { page: InvestmentsDashboardPage; ctx: InvestmentContextService } {
    api = {
      getSecurities: vi.fn().mockReturnValue(of([security])),
      getPortfolios: vi.fn().mockReturnValue(of(portfolios)),
      getPortfolioSummary: vi.fn().mockReturnValue(of(summary)),
      getPositions: vi.fn().mockReturnValue(of([position])),
      getValuationHistory: vi.fn().mockReturnValue(of(history)),
      getInvestmentPerformance: vi.fn().mockReturnValue(of(performance)),
      ...overrides,
    };
    TestBed.configureTestingModule({ providers: [{ provide: ApiService, useValue: api }] });
    const ctx = TestBed.inject(InvestmentContextService);
    const page = TestBed.createComponent(InvestmentsDashboardPage).componentInstance;
    return { page, ctx };
  }

  function fakeCanvas(): ElementRef<HTMLCanvasElement> {
    return { nativeElement: {} } as ElementRef<HTMLCanvasElement>;
  }

  function withCanvases(page: InvestmentsDashboardPage): InvestmentsDashboardPage {
    page.allocationCanvas = fakeCanvas();
    page.evolutionCanvas = fakeCanvas();
    page.pnlCanvas = fakeCanvas();
    page.performanceCanvas = fakeCanvas();
    return page;
  }

  it('ngOnInit inicializa el contexto compartido y carga el resumen de la cartera seleccionada', () => {
    const { page, ctx } = create();
    page.ngOnInit();
    expect(ctx.portfolioId).toBe(1);
    expect(ctx.loaded).toBe(true);
    expect(page.summary).toEqual(summary);
    expect(page.positions).toEqual([position]);
    expect(page.performance).toEqual(performance);
  });

  it('sin carteras, el resumen queda vacío', () => {
    const { page } = create({}, []);
    page.ngOnInit();
    expect(page.summary).toBeNull();
    expect(page.positions).toEqual([]);
  });

  it('reacciona a un cambio de cartera en el contexto compartido (p.ej. desde la barra de herramientas)', () => {
    const { page, ctx } = create();
    page.ngOnInit();
    api.getPortfolioSummary.mockClear();
    const other = { ...summary, portfolioId: 2 };
    api.getPortfolioSummary.mockReturnValue(of(other));
    ctx.portfolios = [portfolio, { id: 2, name: 'Otra', baseCurrency: 'USD' }];
    ctx.portfolioId = 2;
    expect(api.getPortfolioSummary).toHaveBeenCalledWith(2);
    expect(page.summary).toEqual(other);
  });

  it('ngOnDestroy cierra la suscripción al contexto y destruye los gráficos', () => {
    const { page, ctx } = create();
    withCanvases(page);
    page.ngOnInit();
    page.ngAfterViewInit();
    const charts = (page as unknown as { charts: { destroy: ReturnType<typeof vi.fn> }[] }).charts;
    page.ngOnDestroy();
    charts.forEach(c => expect(c.destroy).toHaveBeenCalled());
    api.getPortfolioSummary.mockClear();
    ctx.portfolioId = 999;
    expect(api.getPortfolioSummary).not.toHaveBeenCalled();
  });

  describe('getters', () => {
    it('baseCurrency prioriza el summary, luego la cartera, luego EUR', () => {
      const { page } = create();
      page.ngOnInit();
      expect(page.baseCurrency).toBe('EUR');
      page.summary = { ...summary, baseCurrency: 'USD' };
      expect(page.baseCurrency).toBe('USD');
      page.summary = null;
      expect(page.baseCurrency).toBe(portfolio.baseCurrency);
    });

    it('cashEntries convierte el mapa de efectivo por divisa en una lista', () => {
      const { page } = create();
      page.ngOnInit();
      expect(page.cashEntries).toEqual([{ currency: 'EUR', amount: 1000 }]);
    });

    it('cashEntries está vacío sin summary', () => {
      const { page } = create({}, []);
      page.ngOnInit();
      expect(page.cashEntries).toEqual([]);
    });
  });

  describe('gráficos', () => {
    it('sin datos (sin cartera), solo dibuja evolución y P&L (sin guarda de null)', () => {
      const { page } = create({}, []);
      withCanvases(page);
      page.ngOnInit();
      page.ngAfterViewInit();
      const charts = (page as unknown as { charts: unknown[] }).charts;
      expect(charts.length).toBe(2);
    });

    it('el donut de asignación reparte posiciones abiertas + efectivo', () => {
      const { page } = create();
      withCanvases(page);
      page.ngOnInit();
      page.ngAfterViewInit();
      const charts = (page as unknown as {
        charts: { config: { type: string; data: { labels: string[]; datasets: { data: number[] }[] } } }[]
      }).charts;
      const allocation = charts[0];
      expect(allocation.config.type).toBe('doughnut');
      expect(allocation.config.data.labels).toEqual(['Empresa', 'Efectivo']);
      expect(allocation.config.data.datasets[0].data).toEqual([1500, 1000]);
    });

    it('evolución expone valor y aportado', () => {
      const { page } = create();
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
      const { page } = create({ getPositions: vi.fn().mockReturnValue(of([position, { ...position, securityId: 10, name: 'Perdedora', latentPnl: -200 }])) });
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
      const { page } = create({
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
  });
});
