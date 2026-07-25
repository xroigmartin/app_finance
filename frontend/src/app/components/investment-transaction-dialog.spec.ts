import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { ApiService } from '../api.service';
import { InvestmentSecurity, InvestmentTransactionView } from '../models';
import { InvestmentTransactionDialog } from './investment-transaction-dialog';

describe('InvestmentTransactionDialog', () => {
  let api: {
    getSecurities: ReturnType<typeof vi.fn>;
    createInvestmentTransaction: ReturnType<typeof vi.fn>;
    updateInvestmentTransaction: ReturnType<typeof vi.fn>;
  };

  const security: InvestmentSecurity = {
    id: 9, isin: 'US1', currency: 'EUR', name: 'Empresa', ticker: 'EMP', type: 'EQUITY', exchange: null, figi: null,
  };

  function create(): InvestmentTransactionDialog {
    api = {
      getSecurities: vi.fn().mockReturnValue(of([security])),
      createInvestmentTransaction: vi.fn().mockReturnValue(of({})),
      updateInvestmentTransaction: vi.fn().mockReturnValue(of({})),
    };
    TestBed.configureTestingModule({ providers: [{ provide: ApiService, useValue: api }] });
    const dialog = TestBed.createComponent(InvestmentTransactionDialog).componentInstance;
    dialog.portfolioId = 1;
    dialog.ngOnInit();
    return dialog;
  }

  it('ngOnInit carga los valores', () => {
    const dialog = create();
    expect(dialog.securities).toEqual([security]);
  });

  describe('reglas por tipo de operación', () => {
    it('securityRule: prohibido en aportación/retirada/conversión de divisa', () => {
      const dialog = create();
      expect(dialog.securityRule('DEPOSIT')).toBe('FORBIDDEN');
      expect(dialog.securityRule('WITHDRAWAL')).toBe('FORBIDDEN');
      expect(dialog.securityRule('FX_TRADE')).toBe('FORBIDDEN');
    });

    it('securityRule: opcional en interés/comisión/retención', () => {
      const dialog = create();
      expect(dialog.securityRule('INTEREST')).toBe('OPTIONAL');
      expect(dialog.securityRule('FEE')).toBe('OPTIONAL');
      expect(dialog.securityRule('TAX')).toBe('OPTIONAL');
    });

    it('securityRule: requerido en el resto (compra, venta...)', () => {
      const dialog = create();
      expect(dialog.securityRule('BUY')).toBe('REQUIRED');
      expect(dialog.securityRule('SELL')).toBe('REQUIRED');
      expect(dialog.securityRule('DIVIDEND')).toBe('REQUIRED');
    });

    it('hasQuantity solo en compra/venta/split', () => {
      const dialog = create();
      expect(dialog.hasQuantity('BUY')).toBe(true);
      expect(dialog.hasQuantity('SELL')).toBe(true);
      expect(dialog.hasQuantity('SPLIT')).toBe(true);
      expect(dialog.hasQuantity('DIVIDEND')).toBe(false);
    });

    it('quantityHint según el tipo', () => {
      const dialog = create();
      expect(dialog.quantityHint('BUY')).toBe('(+)');
      expect(dialog.quantityHint('SELL')).toBe('(−)');
      expect(dialog.quantityHint('SPLIT')).toBe('(delta con signo)');
    });

    it('amountHint según el tipo', () => {
      const dialog = create();
      for (const t of ['BUY', 'FEE', 'TAX', 'TRADE_TAX', 'WITHDRAWAL', 'FX_TRADE'] as const) {
        expect(dialog.amountHint(t)).toBe('(−)');
      }
      expect(dialog.amountHint('SPLIT')).toBe('(0)');
      expect(dialog.amountHint('DIVIDEND')).toBe('(+)');
    });
  });

  describe('abrir el formulario', () => {
    it('openNew resetea a una compra nueva con la fecha de hoy y la divisa base', () => {
      const dialog = create();
      dialog.baseCurrency = 'USD';
      dialog.editingId = 99;
      dialog.openNew();
      expect(dialog.visible).toBe(true);
      expect(dialog.editingId).toBeNull();
      expect(dialog.type).toBe('BUY');
      expect(dialog.tradeDate).toBe(new Date().toISOString().slice(0, 10));
      expect(dialog.currency).toBe('USD');
      expect(dialog.amount).toBeNull();
    });

    it('edit precarga todos los campos de la operación', () => {
      const dialog = create();
      const tx: InvestmentTransactionView = {
        id: 5, type: 'BUY', tradeDate: '2026-01-01', securityId: 9, securityName: 'Empresa',
        quantity: 10, price: 100, amount: -1000, currency: 'EUR', counterAmount: null,
        counterCurrency: null, fee: -1, tax: null, fxRateToBase: null, description: 'compra',
      } as InvestmentTransactionView;
      dialog.edit(tx);
      expect(dialog.visible).toBe(true);
      expect(dialog.editingId).toBe(5);
      expect(dialog.quantity).toBe(10);
      expect(dialog.price).toBe(100);
      expect(dialog.fee).toBe(-1);
      expect(dialog.description).toBe('compra');
    });

    it('edit usa cadena vacía si no hay descripción ni divisa de contrapartida', () => {
      const dialog = create();
      const tx: InvestmentTransactionView = {
        id: 5, type: 'DEPOSIT', tradeDate: '2026-01-01', securityId: null, securityName: null,
        quantity: null, price: null, amount: 100, currency: 'EUR', counterAmount: null,
        counterCurrency: null, fee: null, tax: null, fxRateToBase: null, description: null,
      } as InvestmentTransactionView;
      dialog.edit(tx);
      expect(dialog.description).toBe('');
      expect(dialog.counterCurrency).toBe('');
    });

    it('close oculta el diálogo', () => {
      const dialog = create();
      dialog.visible = true;
      dialog.close();
      expect(dialog.visible).toBe(false);
    });
  });

  describe('save', () => {
    it('no hace nada sin fecha o sin importe', () => {
      const dialog = create();
      dialog.openNew();
      dialog.tradeDate = '';
      dialog.amount = 100;
      dialog.save();
      expect(api.createInvestmentTransaction).not.toHaveBeenCalled();

      dialog.tradeDate = '2026-01-01';
      dialog.amount = null;
      dialog.save();
      expect(api.createInvestmentTransaction).not.toHaveBeenCalled();
    });

    it('en una compra, incluye instrumento, cantidad y precio', () => {
      const dialog = create();
      dialog.openNew();
      dialog.type = 'BUY';
      dialog.tradeDate = '2026-01-01';
      dialog.securityId = 9;
      dialog.quantity = 10;
      dialog.price = 100;
      dialog.amount = -1000;
      dialog.currency = ' eur ';
      dialog.save();
      expect(api.createInvestmentTransaction).toHaveBeenCalledWith(1, expect.objectContaining({
        securityId: 9, quantity: 10, price: 100, currency: 'EUR',
      }));
    });

    it('en un split, no manda precio (aunque tenga cantidad)', () => {
      const dialog = create();
      dialog.openNew();
      dialog.type = 'SPLIT';
      dialog.tradeDate = '2026-01-01';
      dialog.securityId = 9;
      dialog.quantity = 2;
      dialog.price = 999;
      dialog.amount = 0;
      dialog.save();
      expect(api.createInvestmentTransaction).toHaveBeenCalledWith(1, expect.objectContaining({
        quantity: 2, price: null,
      }));
    });

    it('en aportación/retirada, no manda instrumento aunque hubiera uno elegido', () => {
      const dialog = create();
      dialog.openNew();
      dialog.type = 'DEPOSIT';
      dialog.tradeDate = '2026-01-01';
      dialog.securityId = 9;
      dialog.amount = 500;
      dialog.save();
      expect(api.createInvestmentTransaction).toHaveBeenCalledWith(1, expect.objectContaining({
        securityId: null, quantity: null, price: null,
      }));
    });

    it('en conversión de divisa, incluye el importe/divisa entrantes recortados y en mayúsculas', () => {
      const dialog = create();
      dialog.openNew();
      dialog.type = 'FX_TRADE';
      dialog.tradeDate = '2026-01-01';
      dialog.amount = -100;
      dialog.counterAmount = 110;
      dialog.counterCurrency = ' usd ';
      dialog.save();
      expect(api.createInvestmentTransaction).toHaveBeenCalledWith(1, expect.objectContaining({
        counterAmount: 110, counterCurrency: 'USD',
      }));
    });

    it('fuera de una conversión de divisa, no manda importe/divisa entrantes', () => {
      const dialog = create();
      dialog.openNew();
      dialog.type = 'BUY';
      dialog.tradeDate = '2026-01-01';
      dialog.securityId = 9;
      dialog.amount = -100;
      dialog.counterAmount = 999;
      dialog.counterCurrency = 'usd';
      dialog.save();
      expect(api.createInvestmentTransaction).toHaveBeenCalledWith(1, expect.objectContaining({
        counterAmount: null, counterCurrency: null,
      }));
    });

    it('recorta la descripción, o la manda como null si queda vacía', () => {
      const dialog = create();
      dialog.openNew();
      dialog.type = 'DEPOSIT';
      dialog.tradeDate = '2026-01-01';
      dialog.amount = 100;
      dialog.description = '  algo  ';
      dialog.save();
      expect(api.createInvestmentTransaction).toHaveBeenCalledWith(1, expect.objectContaining({ description: 'algo' }));

      dialog.description = '   ';
      dialog.save();
      expect(api.createInvestmentTransaction).toHaveBeenCalledWith(1, expect.objectContaining({ description: null }));
    });

    it('actualiza cuando hay editingId', () => {
      const dialog = create();
      const tx: InvestmentTransactionView = {
        id: 5, type: 'DEPOSIT', tradeDate: '2026-01-01', securityId: null, securityName: null,
        quantity: null, price: null, amount: 100, currency: 'EUR', counterAmount: null,
        counterCurrency: null, fee: null, tax: null, fxRateToBase: null, description: null,
      } as InvestmentTransactionView;
      dialog.edit(tx);
      dialog.save();
      expect(api.updateInvestmentTransaction).toHaveBeenCalledWith(5, expect.anything());
    });

    it('en éxito, cierra el diálogo y emite done', () => {
      const dialog = create();
      const onDone = vi.fn();
      dialog.done.subscribe(onDone);
      dialog.openNew();
      dialog.type = 'DEPOSIT';
      dialog.tradeDate = '2026-01-01';
      dialog.amount = 100;
      dialog.save();
      expect(dialog.loading).toBe(false);
      expect(dialog.visible).toBe(false);
      expect(onDone).toHaveBeenCalled();
    });

    it('en error, deja el diálogo abierto y fija el mensaje', () => {
      const dialog = create();
      api.createInvestmentTransaction.mockReturnValue(throwError(() => ({ error: { detail: 'boom' } })));
      dialog.openNew();
      dialog.type = 'DEPOSIT';
      dialog.tradeDate = '2026-01-01';
      dialog.amount = 100;
      dialog.save();
      expect(dialog.loading).toBe(false);
      expect(dialog.visible).toBe(true);
      expect(dialog.error).toBe('boom');
    });

    it('en error sin detail ni message, usa el mensaje genérico', () => {
      const dialog = create();
      api.createInvestmentTransaction.mockReturnValue(throwError(() => ({ error: null })));
      dialog.openNew();
      dialog.type = 'DEPOSIT';
      dialog.tradeDate = '2026-01-01';
      dialog.amount = 100;
      dialog.save();
      expect(dialog.error).toBe('No se pudo guardar la operación.');
    });
  });
});
