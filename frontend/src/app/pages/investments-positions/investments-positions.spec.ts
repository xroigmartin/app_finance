import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { ApiService } from '../../api.service';
import { InvestmentContextService } from '../../investment-context.service';
import { InvestmentPerformance, InvestmentSecurity, Portfolio, PositionView } from '../../models';
import { InvestmentsPositionsPage } from './investments-positions';

describe('InvestmentsPositionsPage', () => {
  afterEach(() => vi.restoreAllMocks());

  let api: {
    getSecurities: ReturnType<typeof vi.fn>;
    getPortfolios: ReturnType<typeof vi.fn>;
    getPositions: ReturnType<typeof vi.fn>;
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
  const performance: InvestmentPerformance = {
    portfolioId: 1, baseCurrency: 'EUR', valuationDate: '2026-07-01', twrPercent: 25, xirrPercent: 20,
    positions: [{ securityId: 9, name: 'Empresa', twrPercent: 25, xirrPercent: 20 }],
  };

  function create(overrides: Partial<typeof api> = {}, portfolios: Portfolio[] = [portfolio]):
    { page: InvestmentsPositionsPage; ctx: InvestmentContextService } {
    api = {
      getSecurities: vi.fn().mockReturnValue(of([security])),
      getPortfolios: vi.fn().mockReturnValue(of(portfolios)),
      getPositions: vi.fn().mockReturnValue(of([position])),
      getInvestmentPerformance: vi.fn().mockReturnValue(of(performance)),
      ...overrides,
    };
    TestBed.configureTestingModule({ providers: [{ provide: ApiService, useValue: api }] });
    const ctx = TestBed.inject(InvestmentContextService);
    const page = TestBed.createComponent(InvestmentsPositionsPage).componentInstance;
    return { page, ctx };
  }

  it('ngOnInit inicializa el contexto compartido y carga las posiciones y la rentabilidad de la cartera seleccionada', () => {
    const { page, ctx } = create();
    page.ngOnInit();
    expect(ctx.portfolioId).toBe(1);
    expect(ctx.loaded).toBe(true);
    expect(page.positions).toEqual([position]);
    expect(page.performance).toEqual(performance);
  });

  it('sin carteras, las posiciones quedan vacías', () => {
    const { page } = create({}, []);
    page.ngOnInit();
    expect(page.positions).toEqual([]);
    expect(page.performance).toBeNull();
  });

  it('reacciona a un cambio de cartera en el contexto compartido (p.ej. desde la barra de herramientas)', () => {
    const { page, ctx } = create();
    page.ngOnInit();
    api.getPositions.mockClear();
    const other = { ...position, securityId: 20, name: 'Otra' };
    api.getPositions.mockReturnValue(of([other]));
    ctx.portfolios = [portfolio, { id: 2, name: 'Otra', baseCurrency: 'USD' }];
    ctx.portfolioId = 2;
    expect(api.getPositions).toHaveBeenCalledWith(2);
    expect(page.positions).toEqual([other]);
  });

  it('ngOnDestroy cierra la suscripción al contexto', () => {
    const { page, ctx } = create();
    page.ngOnInit();
    page.ngOnDestroy();
    api.getPositions.mockClear();
    ctx.portfolioId = 999;
    expect(api.getPositions).not.toHaveBeenCalled();
  });

  it('baseCurrency delega en el contexto compartido', () => {
    const { page } = create();
    page.ngOnInit();
    expect(page.baseCurrency).toBe('EUR');
  });

  it('perfOf busca la rentabilidad de una posición', () => {
    const { page } = create();
    page.ngOnInit();
    expect(page.perfOf(9)?.twrPercent).toBe(25);
    expect(page.perfOf(999)).toBeNull();
  });

  describe('ayudas visuales de posición', () => {
    it('tickerLabel usa el ticker o el nombre, en mayúsculas y máximo 4', () => {
      const { page } = create();
      expect(page.tickerLabel(position)).toBe('EMP');
      expect(page.tickerLabel({ ...position, ticker: null, name: 'empresa' })).toBe('EMPR');
    });

    it('tickerColor cicla la paleta y tickerSoft le añade alfa', () => {
      const { page } = create();
      expect(page.tickerColor(0)).toBe('#2563EB');
      expect(page.tickerSoft(0)).toBe('#2563EB24');
    });
  });
});
