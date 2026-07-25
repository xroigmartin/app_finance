import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { ApiService } from '../api.service';
import { InvestmentContextService } from '../investment-context.service';
import { InvestmentTransactionView, Portfolio } from '../models';
import { InvestmentToolbar } from './investment-toolbar';

describe('InvestmentToolbar', () => {
  const portfolio: Portfolio = { id: 1, name: 'Cartera E2E', baseCurrency: 'EUR' };

  function create(): { toolbar: InvestmentToolbar; ctx: InvestmentContextService } {
    const api = {
      getSecurities: vi.fn().mockReturnValue(of([])),
      getPortfolios: vi.fn().mockReturnValue(of([portfolio])),
      createPortfolio: vi.fn().mockReturnValue(of({ ...portfolio, id: 2 })),
    };
    TestBed.configureTestingModule({ providers: [{ provide: ApiService, useValue: api }] });
    const ctx = TestBed.inject(InvestmentContextService);
    ctx.init();
    const toolbar = TestBed.createComponent(InvestmentToolbar).componentInstance;
    return { toolbar, ctx };
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
});
