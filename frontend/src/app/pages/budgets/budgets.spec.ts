import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { ApiService } from '../../api.service';
import { Account, AnnualBudget, AnnualRow, MonthCell } from '../../models';
import { BudgetsPage } from './budgets';

function months(budget: number, actual: number, budgetId: number | null = null): MonthCell[] {
  return Array.from({ length: 12 }, () => ({ budgetId, budget, actual }));
}

function row(categoryId: number, category: string, type: 'INCOME' | 'EXPENSE', cells: MonthCell[]): AnnualRow {
  return { categoryId, category, color: '#000', type, editable: true, months: cells, children: [] };
}

describe('BudgetsPage', () => {
  let api: {
    getAccounts: ReturnType<typeof vi.fn>;
    getAnnualBudget: ReturnType<typeof vi.fn>;
    createBudget: ReturnType<typeof vi.fn>;
    updateBudget: ReturnType<typeof vi.fn>;
    deleteBudget: ReturnType<typeof vi.fn>;
  };

  const account: Account = { id: 1, name: 'Corriente', type: 'Banco', initialBalance: 0 };
  const incomeRow = row(10, 'Nómina', 'INCOME', months(2000, 2500, 5));
  const expenseRow = row(20, 'Alimentación', 'EXPENSE', months(300, 350, null));
  const data: AnnualBudget = { year: 2026, accountId: 1, income: [incomeRow], expense: [expenseRow] };

  function create(accounts: Account[] | null = [account], budget = of(data)): BudgetsPage {
    api = {
      getAccounts: vi.fn().mockReturnValue(of(accounts ?? [])),
      getAnnualBudget: vi.fn().mockReturnValue(budget),
      createBudget: vi.fn().mockReturnValue(of({})),
      updateBudget: vi.fn().mockReturnValue(of({})),
      deleteBudget: vi.fn().mockReturnValue(of(undefined)),
    };
    TestBed.configureTestingModule({ providers: [{ provide: ApiService, useValue: api }] });
    const page = TestBed.createComponent(BudgetsPage).componentInstance;
    page.ngOnInit();
    return page;
  }

  it('ngOnInit carga cuentas, selecciona la primera y carga el presupuesto anual', () => {
    const page = create();
    expect(page.accountId).toBe(1);
    expect(api.getAnnualBudget).toHaveBeenCalledWith(2026, 1);
    expect(page.data).toEqual(data);
  });

  it('sin cuentas, accountId queda a null y el presupuesto no es editable', () => {
    const page = create([]);
    expect(page.accountId).toBeNull();
    expect(page.editable).toBe(false);
    expect(page.selectedAccountName).toBe('Todas las cuentas');
  });

  it('selectedAccountName resuelve el nombre de la cuenta seleccionada', () => {
    const page = create();
    expect(page.selectedAccountName).toBe('Corriente');
  });

  it('en error de carga, fija un mensaje de error', () => {
    const page = create([account], throwError(() => new Error('down')));
    expect(page.error).toBe('No se pudo cargar el presupuesto anual.');
  });

  it('prevYear/nextYear cambian el año y recargan', () => {
    const page = create();
    api.getAnnualBudget.mockClear();
    page.nextYear();
    expect(page.year).toBe(2027);
    expect(api.getAnnualBudget).toHaveBeenCalledWith(2027, 1);

    page.prevYear();
    page.prevYear();
    expect(page.year).toBe(2025);
  });

  it('onAccountChange recarga con la cuenta seleccionada', () => {
    const page = create();
    page.accountId = null;
    api.getAnnualBudget.mockClear();
    page.onAccountChange();
    expect(api.getAnnualBudget).toHaveBeenCalledWith(2026, undefined);
  });

  describe('cálculos derivados', () => {
    it('diff() es actual menos budget', () => {
      const page = create();
      expect(page.diff(120, 100)).toBe(20);
    });

    it('diffClass: sin diferencia, sin clase', () => {
      const page = create();
      expect(page.diffClass(0, 'INCOME')).toBe('');
    });

    it('diffClass: en ingresos, de más es bueno', () => {
      const page = create();
      expect(page.diffClass(10, 'INCOME')).toBe('amount-income');
      expect(page.diffClass(-10, 'INCOME')).toBe('amount-expense');
    });

    it('diffClass: en gastos, de menos es bueno', () => {
      const page = create();
      expect(page.diffClass(-10, 'EXPENSE')).toBe('amount-income');
      expect(page.diffClass(10, 'EXPENSE')).toBe('amount-expense');
    });

    it('rate() es el ahorro sobre el ingreso, o null sin ingreso', () => {
      const page = create();
      expect(page.rate(50, 200)).toBe(0.25);
      expect(page.rate(50, 0)).toBeNull();
    });

    it('fmt() deja vacíos los ceros', () => {
      const page = create();
      expect(page.fmt(0)).toBe('');
      expect(page.fmt(1234.5)).toBe('1234,5');
    });

    it('pct() formatea o deja vacío si es null', () => {
      const page = create();
      expect(page.pct(null)).toBe('');
      expect(page.pct(0.256)).toBe('25,6%');
    });

    it('recompute agrega ingresos/gastos y calcula ahorro y acumulado mes a mes', () => {
      const page = create();
      expect(page.income.budget[0]).toBe(2000);
      expect(page.expense.actual[0]).toBe(350);
      expect(page.savings.actual[0]).toBe(2500 - 350);
      expect(page.cumulative.actual[0]).toBe(2500 - 350);
      expect(page.cumulative.actual[1]).toBe((2500 - 350) * 2);
      expect(page.savings.totalActual).toBe((2500 - 350) * 12);
      expect(page.cumulative.totalActual).toBe(page.savings.totalActual);
    });
  });

  describe('onCellEdit', () => {
    it('no hace nada si no hay cuenta seleccionada (no editable)', () => {
      const page = create([]);
      page.onCellEdit(incomeRow, 0, { target: { value: '100' } } as unknown as Event);
      expect(api.createBudget).not.toHaveBeenCalled();
      expect(api.updateBudget).not.toHaveBeenCalled();
    });

    it('un importe no numérico fija un error y recarga', () => {
      const page = create();
      api.getAnnualBudget.mockClear();
      page.onCellEdit(incomeRow, 0, { target: { value: 'abc' } } as unknown as Event);
      expect(page.error).toContain('no válido');
      expect(api.getAnnualBudget).toHaveBeenCalled();
    });

    it('un importe negativo fija un error', () => {
      const page = create();
      page.onCellEdit(incomeRow, 0, { target: { value: '-5' } } as unknown as Event);
      expect(page.error).toContain('no válido');
    });

    it('vaciar la celda de una con presupuesto lo borra', () => {
      const page = create();
      page.onCellEdit(incomeRow, 0, { target: { value: '' } } as unknown as Event);
      expect(api.deleteBudget).toHaveBeenCalledWith(5);
    });

    it('vaciar una celda sin presupuesto no llama a la API', () => {
      const page = create();
      page.onCellEdit(expenseRow, 0, { target: { value: '' } } as unknown as Event);
      expect(api.deleteBudget).not.toHaveBeenCalled();
      expect(api.createBudget).not.toHaveBeenCalled();
    });

    it('crea el presupuesto de una celda que no tenía (budgetId null)', () => {
      const page = create();
      page.onCellEdit(expenseRow, 2, { target: { value: '250' } } as unknown as Event);
      expect(api.createBudget).toHaveBeenCalledWith({
        accountId: 1, categoryId: 20, year: 2026, month: 3, amount: 250,
      });
    });

    it('actualiza el presupuesto de una celda existente (budgetId presente)', () => {
      const page = create();
      page.onCellEdit(incomeRow, 0, { target: { value: '2200' } } as unknown as Event);
      expect(api.updateBudget).toHaveBeenCalledWith(5, {
        accountId: 1, categoryId: 10, year: 2026, month: 1, amount: 2200,
      });
    });

    it('en error al guardar, fija un mensaje y recarga', () => {
      const page = create();
      api.createBudget.mockReturnValue(throwError(() => new Error('down')));
      page.onCellEdit(expenseRow, 2, { target: { value: '250' } } as unknown as Event);
      expect(page.error).toBe('Error al guardar el presupuesto.');
    });
  });
});
