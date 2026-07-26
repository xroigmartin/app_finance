import { TestBed } from '@angular/core/testing';
import { of, Subject, throwError } from 'rxjs';
import { ApiService } from '../api.service';
import { InvestmentContextService } from '../investment-context.service';
import { InvestmentTransactionView, Portfolio } from '../models';
import { InvestmentToolbar } from './investment-toolbar';

describe('InvestmentToolbar', () => {
  const portfolio: Portfolio = { id: 1, name: 'Cartera E2E', baseCurrency: 'EUR' };

  function create(apiOverrides: Record<string, unknown> = {}): {
    toolbar: InvestmentToolbar; ctx: InvestmentContextService; api: Record<string, ReturnType<typeof vi.fn>>;
  } {
    const api = {
      getSecurities: vi.fn().mockReturnValue(of([])),
      getPortfolios: vi.fn().mockReturnValue(of([portfolio])),
      createPortfolio: vi.fn().mockReturnValue(of({ ...portfolio, id: 2 })),
      refreshPrices: vi.fn().mockReturnValue(of({ updated: 0, failed: [] })),
      ...apiOverrides,
    };
    TestBed.configureTestingModule({ providers: [{ provide: ApiService, useValue: api }] });
    const ctx = TestBed.inject(InvestmentContextService);
    ctx.init();
    const toolbar = TestBed.createComponent(InvestmentToolbar).componentInstance;
    return { toolbar, ctx, api };
  }

  it('expone el contexto compartido a la plantilla', () => {
    const { toolbar, ctx } = create();
    expect(toolbar.ctx).toBe(ctx);
    expect(toolbar.ctx.portfolioId).toBe(1);
  });

  it('edit() delega en el diálogo de operaciones cuando está montado', () => {
    const { toolbar } = create();
    const edit = vi.fn();
    toolbar.txDialog = { edit } as unknown as InvestmentToolbar['txDialog'];
    const tx = { id: 5 } as InvestmentTransactionView;
    toolbar.edit(tx);
    expect(edit).toHaveBeenCalledWith(tx);
  });

  it('edit() no revienta si el diálogo aún no está montado', () => {
    const { toolbar } = create();
    expect(() => toolbar.edit({ id: 5 } as InvestmentTransactionView)).not.toThrow();
  });

  it('refreshPrices() marca estado de carga mientras la petición está en curso', () => {
    const { toolbar, api } = create();
    api['refreshPrices'].mockReturnValue(new Subject());

    toolbar.refreshPrices();

    expect(toolbar.refreshingPrices).toBe(true);
    expect(api['refreshPrices']).toHaveBeenCalled();
  });

  it('refreshPrices() guarda el resumen, desactiva la carga y emite done al terminar', () => {
    const { toolbar } = create({
      refreshPrices: vi.fn().mockReturnValue(
        of({ updated: 12, failed: [{ securityId: 9, ticker: 'ZEG', reason: 'Sin cotización disponible' }] })),
    });
    const doneSpy = vi.fn();
    toolbar.done.subscribe(doneSpy);

    toolbar.refreshPrices();

    expect(toolbar.refreshingPrices).toBe(false);
    expect(toolbar.priceRefreshResult?.updated).toBe(12);
    expect(toolbar.priceRefreshResult?.failed).toHaveLength(1);
    expect(doneSpy).toHaveBeenCalled();
  });

  it('refreshPrices() no revienta y desactiva la carga si la petición falla', () => {
    const { toolbar } = create({
      refreshPrices: vi.fn().mockReturnValue(throwError(() => new Error('network'))),
    });

    expect(() => toolbar.refreshPrices()).not.toThrow();
    expect(toolbar.refreshingPrices).toBe(false);
    expect(toolbar.priceRefreshError).toBeTruthy();
  });
});
