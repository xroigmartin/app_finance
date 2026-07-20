import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { ApiService } from '../../api.service';
import { Account, Transfer } from '../../models';
import { TransfersPage } from './transfers';

describe('TransfersPage', () => {
  afterEach(() => vi.restoreAllMocks());

  let api: {
    getAccounts: ReturnType<typeof vi.fn>;
    getTransfers: ReturnType<typeof vi.fn>;
    createTransfer: ReturnType<typeof vi.fn>;
    updateTransfer: ReturnType<typeof vi.fn>;
    deleteTransfer: ReturnType<typeof vi.fn>;
  };

  const accountA: Account = { id: 1, name: 'Corriente', type: 'Banco', initialBalance: 0 };
  const accountB: Account = { id: 2, name: 'Ahorro', type: 'Efectivo', initialBalance: 0 };
  const transfer: Transfer = {
    id: 9, date: '2026-07-01', amount: 100, description: 'Traspaso', fromAccount: accountA, toAccount: accountB,
  };

  function create(): TransfersPage {
    api = {
      getAccounts: vi.fn().mockReturnValue(of([accountA, accountB])),
      getTransfers: vi.fn().mockReturnValue(of([transfer])),
      createTransfer: vi.fn().mockReturnValue(of(transfer)),
      updateTransfer: vi.fn().mockReturnValue(of(transfer)),
      deleteTransfer: vi.fn().mockReturnValue(of(undefined)),
    };
    TestBed.configureTestingModule({ providers: [{ provide: ApiService, useValue: api }] });
    const page = TestBed.createComponent(TransfersPage).componentInstance;
    page.ngOnInit();
    return page;
  }

  it('ngOnInit carga cuentas y transferencias', () => {
    const page = create();
    expect(page.accounts).toEqual([accountA, accountB]);
    expect(page.transfers).toEqual([transfer]);
  });

  it('openNew resetea el formulario con las dos primeras cuentas', () => {
    const page = create();
    page.openNew();
    expect(page.showForm).toBe(true);
    expect(page.editingId).toBeNull();
    expect(page.error).toBe('');
    expect(page.form.fromAccountId).toBe(1);
    expect(page.form.toAccountId).toBe(2);
    expect(page.form.amount).toBe(0);
  });

  it('openEdit precarga el formulario desde la transferencia', () => {
    const page = create();
    page.openEdit(transfer);
    expect(page.editingId).toBe(9);
    expect(page.showForm).toBe(true);
    expect(page.form).toEqual({
      date: '2026-07-01', amount: 100, description: 'Traspaso', fromAccountId: 1, toAccountId: 2,
    });
  });

  describe('save', () => {
    it('rechaza origen y destino iguales sin llamar a la API', () => {
      const page = create();
      page.form = { date: '2026-07-01', amount: 10, description: null, fromAccountId: 1, toAccountId: 1 };
      page.save();
      expect(page.error).toContain('distintas');
      expect(api.createTransfer).not.toHaveBeenCalled();
    });

    it('rechaza un importe no numérico sin llamar a la API', () => {
      const page = create();
      page.form = { date: '2026-07-01', amount: 'abc' as unknown as number, description: null, fromAccountId: 1, toAccountId: 2 };
      page.save();
      expect(page.error).toContain('no válido');
      expect(api.createTransfer).not.toHaveBeenCalled();
    });

    it('rechaza un importe <= 0', () => {
      const page = create();
      page.form = { date: '2026-07-01', amount: 0, description: null, fromAccountId: 1, toAccountId: 2 };
      page.save();
      expect(page.error).toContain('no válido');
      expect(api.createTransfer).not.toHaveBeenCalled();
    });

    it('crea cuando no hay editingId, cierra el formulario y recarga', () => {
      const page = create();
      page.form = { date: '2026-07-01', amount: 50, description: 'x', fromAccountId: 1, toAccountId: 2 };
      page.save();
      expect(api.createTransfer).toHaveBeenCalledWith(page.form);
      expect(page.showForm).toBe(false);
      expect(api.getTransfers).toHaveBeenCalledTimes(2); // ngOnInit + save()
    });

    it('actualiza cuando hay editingId', () => {
      const page = create();
      page.openEdit(transfer);
      page.form.amount = 200;
      page.save();
      expect(api.updateTransfer).toHaveBeenCalledWith(9, page.form);
    });

    it('en error, usa el detail de la API si existe', () => {
      const page = create();
      api.createTransfer.mockReturnValue(throwError(() => ({ error: { detail: 'boom' } })));
      page.form = { date: '2026-07-01', amount: 50, description: null, fromAccountId: 1, toAccountId: 2 };
      page.save();
      expect(page.error).toBe('boom');
    });

    it('en error sin detail, usa message; sin ninguno, el mensaje genérico', () => {
      const page = create();
      api.createTransfer.mockReturnValue(throwError(() => ({ error: { message: 'msg' } })));
      page.form = { date: '2026-07-01', amount: 50, description: null, fromAccountId: 1, toAccountId: 2 };
      page.save();
      expect(page.error).toBe('msg');

      api.createTransfer.mockReturnValue(throwError(() => ({ error: null })));
      page.save();
      expect(page.error).toBe('Error al guardar la transferencia.');
    });
  });

  describe('remove', () => {
    it('no borra si no se confirma', () => {
      vi.spyOn(window, 'confirm').mockReturnValue(false);
      const page = create();
      page.remove(transfer);
      expect(api.deleteTransfer).not.toHaveBeenCalled();
    });

    it('borra y recarga si se confirma', () => {
      vi.spyOn(window, 'confirm').mockReturnValue(true);
      const page = create();
      page.remove(transfer);
      expect(api.deleteTransfer).toHaveBeenCalledWith(9);
    });
  });
});
