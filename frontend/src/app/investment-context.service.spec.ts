import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { ApiService } from './api.service';
import { Portfolio } from './models';
import { InvestmentContextService } from './investment-context.service';

describe('InvestmentContextService', () => {
  let api: {
    getSecurities: ReturnType<typeof vi.fn>;
    getPortfolios: ReturnType<typeof vi.fn>;
    createPortfolio: ReturnType<typeof vi.fn>;
  };

  const portfolio: Portfolio = { id: 1, name: 'Cartera E2E', baseCurrency: 'EUR' };
  const security = { id: 9, isin: 'US1', currency: 'EUR', name: 'Empresa', ticker: 'EMP', type: 'EQUITY' as const, exchange: 'XETRA', figi: null };

  function create(overrides: Partial<typeof api> = {}, portfolios: Portfolio[] = [portfolio]): InvestmentContextService {
    api = {
      getSecurities: vi.fn().mockReturnValue(of([security])),
      getPortfolios: vi.fn().mockReturnValue(of(portfolios)),
      createPortfolio: vi.fn().mockReturnValue(of({ ...portfolio, id: 2 })),
      ...overrides,
    };
    TestBed.configureTestingModule({ providers: [{ provide: ApiService, useValue: api }] });
    return TestBed.inject(InvestmentContextService);
  }

  it('init() carga carteras e instrumentos, y selecciona la primera cartera', () => {
    const ctx = create();
    ctx.init();
    expect(ctx.securities).toEqual([security]);
    expect(ctx.portfolios).toEqual([portfolio]);
    expect(ctx.portfolioId).toBe(1);
    expect(ctx.loaded).toBe(true);
  });

  it('sin carteras, portfolioId queda a null', () => {
    const ctx = create({}, []);
    ctx.init();
    expect(ctx.portfolioId).toBeNull();
    expect(ctx.loaded).toBe(true);
  });

  it('init() no pisa una cartera ya seleccionada si sigue existiendo', () => {
    const other: Portfolio = { id: 2, name: 'Otra', baseCurrency: 'USD' };
    const ctx = create({}, [portfolio, other]);
    ctx.portfolioId = 2;
    ctx.init();
    expect(ctx.portfolioId).toBe(2);
  });

  it('init() cae a la primera cartera si la seleccionada ya no existe', () => {
    const ctx = create({}, [portfolio]);
    ctx.portfolioId = 999;
    ctx.init();
    expect(ctx.portfolioId).toBe(1);
  });

  it('portfolioId$ emite el valor actual al suscribirse y cada cambio posterior', () => {
    const ctx = create();
    const emitted: (number | null)[] = [];
    ctx.portfolioId$.subscribe(id => emitted.push(id));
    ctx.init();
    ctx.portfolioId = 7;
    expect(emitted).toEqual([null, 1, 7]);
  });

  describe('getters', () => {
    it('portfolio resuelve la cartera seleccionada', () => {
      const ctx = create();
      ctx.init();
      expect(ctx.portfolio).toEqual(portfolio);
    });

    it('baseCurrency usa la divisa de la cartera, o EUR sin cartera', () => {
      const ctx = create();
      ctx.init();
      expect(ctx.baseCurrency).toBe('EUR');
      ctx.portfolioId = null;
      expect(ctx.baseCurrency).toBe('EUR');
    });
  });

  describe('alta de cartera', () => {
    it('startCreate resetea el formulario', () => {
      const ctx = create();
      ctx.createError = 'algo';
      ctx.startCreate();
      expect(ctx.creating).toBe(true);
      expect(ctx.newName).toBe('');
      expect(ctx.newCurrency).toBe('EUR');
      expect(ctx.createError).toBe('');
    });

    it('cancelCreate cierra el formulario', () => {
      const ctx = create();
      ctx.creating = true;
      ctx.cancelCreate();
      expect(ctx.creating).toBe(false);
    });

    it('createPortfolio no hace nada sin nombre o divisa', () => {
      const ctx = create();
      ctx.newName = '  ';
      ctx.newCurrency = 'EUR';
      ctx.createPortfolio();
      expect(api.createPortfolio).not.toHaveBeenCalled();
    });

    it('createPortfolio crea, la añade y la selecciona', () => {
      const ctx = create();
      ctx.init();
      ctx.newName = 'Nueva';
      ctx.newCurrency = 'usd';
      ctx.createPortfolio();
      expect(api.createPortfolio).toHaveBeenCalledWith({ name: 'Nueva', baseCurrency: 'USD' });
      expect(ctx.portfolioId).toBe(2);
      expect(ctx.creating).toBe(false);
    });

    it('en error, fija createError con el detalle de la API', () => {
      const ctx = create({
        createPortfolio: vi.fn().mockReturnValue(throwError(() => ({ error: { detail: 'boom' } }))),
      });
      ctx.newName = 'Nueva';
      ctx.createPortfolio();
      expect(ctx.createError).toBe('boom');
    });
  });
});
