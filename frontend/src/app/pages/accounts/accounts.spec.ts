import { ElementRef } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { ApiService } from '../../api.service';
import { Account, Summary } from '../../models';
import { AccountsPage } from './accounts';

describe('AccountsPage', () => {
  let api: {
    getAccounts: ReturnType<typeof vi.fn>;
    getSummary: ReturnType<typeof vi.fn>;
    createAccount: ReturnType<typeof vi.fn>;
    updateAccount: ReturnType<typeof vi.fn>;
    deleteAccount: ReturnType<typeof vi.fn>;
  };

  const bank: Account = { id: 1, name: 'Corriente', type: 'Banco', initialBalance: 0 };
  const cash: Account = { id: 2, name: 'Cartera', type: 'Efectivo', initialBalance: 0 };
  const card: Account = { id: 3, name: 'Visa', type: 'Tarjeta', initialBalance: 0 };
  const summary: Summary = {
    totalBalance: 0, monthIncome: 0, monthExpense: 0, monthSavings: 0, yearIncome: 0, yearExpense: 0,
    yearSavings: 0, monthBalanceDelta: 0, yearBalanceDelta: 0, monthGrowthPct: 5, yearGrowthPct: null,
    monthSavingsYieldPct: null, yearSavingsYieldPct: null,
    accounts: [
      { id: 1, name: 'Corriente', type: 'Banco', balance: 1000 },
      { id: 2, name: 'Cartera', type: 'Efectivo', balance: 500 },
      { id: 3, name: 'Visa', type: 'Tarjeta', balance: -200 },
    ],
  };

  function create(accounts: Account[] = [bank, cash, card], summaryResult = of(summary)): AccountsPage {
    api = {
      getAccounts: vi.fn().mockReturnValue(of(accounts)),
      getSummary: vi.fn().mockReturnValue(summaryResult),
      createAccount: vi.fn().mockReturnValue(of(bank)),
      updateAccount: vi.fn().mockReturnValue(of(bank)),
      deleteAccount: vi.fn().mockReturnValue(of(undefined)),
    };
    TestBed.configureTestingModule({ providers: [{ provide: ApiService, useValue: api }] });
    const page = TestBed.createComponent(AccountsPage).componentInstance;
    page.ngOnInit();
    return page;
  }

  function fakeDialog(page: AccountsPage): { showModal: ReturnType<typeof vi.fn>; close: ReturnType<typeof vi.fn> } {
    const dialog = { showModal: vi.fn(), close: vi.fn() };
    page.editDialog = { nativeElement: dialog } as unknown as ElementRef<HTMLDialogElement>;
    return dialog;
  }

  it('agrupa las cuentas por tipo con su saldo actual y calcula patrimonio/activo/pasivo', () => {
    const page = create();
    expect(page.groups.map(g => g.type)).toEqual(['Banco', 'Efectivo', 'Tarjeta']);
    expect(page.totalAssets).toBe(1500);
    expect(page.totalLiabilities).toBe(-200);
    expect(page.netWorth).toBe(1300);
    expect(page.deltaPct).toBe(5);
  });

  it('reparte el porcentaje de la barra de distribución solo entre grupos de activo positivo', () => {
    const page = create();
    const bankGroup = page.groups.find(g => g.type === 'Banco')!;
    const cashGroup = page.groups.find(g => g.type === 'Efectivo')!;
    expect(bankGroup.pct).toBeCloseTo((1000 / 1500) * 100);
    expect(cashGroup.pct).toBeCloseTo((500 / 1500) * 100);
    expect(page.distribution.map(g => g.type)).toEqual(['Banco', 'Efectivo']);
  });

  it('sin saldo positivo, el porcentaje de cada grupo es 0', () => {
    const negativeSummary: Summary = {
      ...summary,
      accounts: [{ id: 1, name: 'Corriente', type: 'Banco', balance: -50 }],
    };
    const page = create([bank], of(negativeSummary));
    expect(page.groups[0].pct).toBe(0);
    expect(page.distribution).toEqual([]);
  });

  it('sin summary disponible (falla la API), degrada a cuentas sin saldo', () => {
    const page = create([bank], throwError(() => new Error('down')));
    expect(page.summary).toBeNull();
    expect(page.groups[0].rows[0].balance).toBeNull();
    expect(page.netWorth).toBe(0);
    expect(page.deltaPct).toBeNull();
  });

  it('una cuenta sin saldo en el summary muestra null, no la excluye del grupo', () => {
    const orphan: Account = { id: 9, name: 'Nueva', type: 'Banco', initialBalance: 0 };
    const page = create([bank, orphan]);
    const row = page.groups[0].rows.find(r => r.account.id === 9)!;
    expect(row.balance).toBeNull();
  });

  it('openNew resetea el formulario y abre el diálogo', () => {
    const page = create();
    const dialog = fakeDialog(page);
    page.openNew();
    expect(page.editingId).toBeNull();
    expect(page.form).toEqual({ name: '', type: 'Banco', initialBalance: 0 });
    expect(dialog.showModal).toHaveBeenCalled();
  });

  it('openEdit precarga el formulario y abre el diálogo', () => {
    const page = create();
    const dialog = fakeDialog(page);
    page.openEdit(bank);
    expect(page.editingId).toBe(1);
    expect(page.form).toEqual(bank);
    expect(dialog.showModal).toHaveBeenCalled();
  });

  it('closeForm limpia el error y cierra el diálogo', () => {
    const page = create();
    const dialog = fakeDialog(page);
    page.error = 'algo';
    page.closeForm();
    expect(page.error).toBe('');
    expect(dialog.close).toHaveBeenCalled();
  });

  it('onCancel limpia el error', () => {
    const page = create();
    page.error = 'algo';
    page.onCancel(new Event('cancel'));
    expect(page.error).toBe('');
  });

  describe('save', () => {
    it('rechaza un saldo inicial no numérico', () => {
      const page = create();
      page.form = { name: 'x', type: 'Banco', initialBalance: 'abc' as unknown as number };
      page.save();
      expect(page.error).toContain('no válido');
      expect(api.createAccount).not.toHaveBeenCalled();
    });

    it('crea cuando no hay editingId', () => {
      const page = create();
      fakeDialog(page);
      page.form = { name: 'Nueva', type: 'Banco', initialBalance: '100,50' as unknown as number };
      page.save();
      expect(api.createAccount).toHaveBeenCalledWith({ name: 'Nueva', type: 'Banco', initialBalance: 100.5 });
    });

    it('actualiza cuando hay editingId', () => {
      const page = create();
      fakeDialog(page);
      page.openEdit(bank);
      page.save();
      expect(api.updateAccount).toHaveBeenCalledWith(1, expect.objectContaining({ name: 'Corriente' }));
    });

    it('en error, muestra el detail de la API', () => {
      const page = create();
      fakeDialog(page);
      api.createAccount.mockReturnValue(throwError(() => ({ error: { detail: 'boom' } })));
      page.form = { name: 'x', type: 'Banco', initialBalance: 0 };
      page.save();
      expect(page.error).toBe('boom');
    });
  });

  describe('remove', () => {
    it('no borra si no se confirma', () => {
      vi.spyOn(window, 'confirm').mockReturnValue(false);
      const page = create();
      page.remove(bank);
      expect(api.deleteAccount).not.toHaveBeenCalled();
    });

    it('para la propagación del evento si se le pasa uno', () => {
      vi.spyOn(window, 'confirm').mockReturnValue(false);
      const page = create();
      const event = { stopPropagation: vi.fn() } as unknown as Event;
      page.remove(bank, event);
      expect(event.stopPropagation).toHaveBeenCalled();
    });

    it('borra y recarga si se confirma', () => {
      vi.spyOn(window, 'confirm').mockReturnValue(true);
      const page = create();
      page.remove(bank);
      expect(api.deleteAccount).toHaveBeenCalledWith(1);
    });

    it('en conflicto (409), avisa de que tiene movimientos asociados', () => {
      vi.spyOn(window, 'confirm').mockReturnValue(true);
      vi.spyOn(window, 'alert').mockImplementation(() => {});
      const page = create();
      api.deleteAccount.mockReturnValue(throwError(() => ({ status: 409 })));
      page.remove(bank);
      expect(window.alert).toHaveBeenCalledWith('La cuenta tiene movimientos asociados y no se puede eliminar.');
    });

    it('en otro error, avisa con el mensaje genérico', () => {
      vi.spyOn(window, 'confirm').mockReturnValue(true);
      vi.spyOn(window, 'alert').mockImplementation(() => {});
      const page = create();
      api.deleteAccount.mockReturnValue(throwError(() => ({ status: 500 })));
      page.remove(bank);
      expect(window.alert).toHaveBeenCalledWith('Error al eliminar la cuenta.');
    });
  });
});
