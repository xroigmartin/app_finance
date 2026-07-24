import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { ApiService } from '../../api.service';
import { Account, Category, Movement, PageResponse, Transaction, Transfer } from '../../models';
import { TransactionsPage } from './transactions';

/** Mirrors the backend's combined ordering (newest first; ties by id desc), for the mocked getMovements. */
function mergeForTest(txs: Transaction[], trs: Transfer[]): Movement[] {
  const rows: Movement[] = [
    ...txs.map(t => ({ source: 'tx' as const, date: t.date, id: t.id!, tx: t, tr: null })),
    ...trs.map(t => ({ source: 'tr' as const, date: t.date, id: t.id!, tx: null, tr: t })),
  ];
  return rows.sort((a, b) => b.date.localeCompare(a.date) || b.id - a.id);
}

function pageOf(content: Movement[]): PageResponse<Movement> {
  return { content, page: 0, size: 25, totalElements: content.length, totalPages: 1 };
}

describe('TransactionsPage', () => {
  afterEach(() => vi.restoreAllMocks());

  let api: {
    getAccounts: ReturnType<typeof vi.fn>;
    getCategories: ReturnType<typeof vi.fn>;
    getTransactions: ReturnType<typeof vi.fn>;
    getMovements: ReturnType<typeof vi.fn>;
    createTransaction: ReturnType<typeof vi.fn>;
    updateTransaction: ReturnType<typeof vi.fn>;
    deleteTransaction: ReturnType<typeof vi.fn>;
    createTransfer: ReturnType<typeof vi.fn>;
    updateTransfer: ReturnType<typeof vi.fn>;
    deleteTransfer: ReturnType<typeof vi.fn>;
  };

  const acc1: Account = { id: 1, name: 'Corriente', type: 'Banco', initialBalance: 0 };
  const acc2: Account = { id: 2, name: 'Ahorro', type: 'Efectivo', initialBalance: 0 };

  const catExpense: Category = { id: 100, name: 'Alimentación', type: 'EXPENSE', color: '#a00' };
  const catExpenseSub: Category = {
    id: 101, name: 'Supermercado', type: 'EXPENSE', color: '#a00', parent: catExpense,
  };
  const catExpenseBound: Category = {
    id: 102, name: 'Propia', type: 'EXPENSE', color: '#a00', account: acc1,
  };
  const catIncome: Category = { id: 200, name: 'Nómina', type: 'INCOME', color: '#0a0' };
  const categories: Category[] = [catExpense, catExpenseSub, catExpenseBound, catIncome];

  const txA: Transaction = {
    id: 1, date: '2026-07-01', amount: 100, description: 'Compra grande', type: 'EXPENSE',
    account: acc1, category: catExpense,
  };
  const txRefundOfA: Transaction = {
    id: 2, date: '2026-07-05', amount: 30, description: 'Devolución parcial', type: 'EXPENSE',
    account: acc1, category: catExpense, refundOf: { id: 1 } as Transaction,
  };
  const txB: Transaction = {
    id: 3, date: '2026-07-03', amount: 200, description: 'Nómina julio', type: 'INCOME',
    account: acc1, category: catIncome,
  };
  const transactions: Transaction[] = [txA, txRefundOfA, txB];

  const trX: Transfer = {
    id: 10, date: '2026-07-02', amount: 500, description: 'Traspaso', fromAccount: acc1, toAccount: acc2,
  };

  function create(txs: Transaction[] = transactions, trs: Transfer[] = [trX]): TransactionsPage {
    api = {
      getAccounts: vi.fn().mockReturnValue(of([acc1, acc2])),
      getCategories: vi.fn().mockReturnValue(of(categories)),
      getTransactions: vi.fn().mockReturnValue(of(txs)),
      getMovements: vi.fn().mockReturnValue(of(pageOf(mergeForTest(txs, trs)))),
      createTransaction: vi.fn().mockReturnValue(of(txA)),
      updateTransaction: vi.fn().mockReturnValue(of(txA)),
      deleteTransaction: vi.fn().mockReturnValue(of(undefined)),
      createTransfer: vi.fn().mockReturnValue(of(trX)),
      updateTransfer: vi.fn().mockReturnValue(of(trX)),
      deleteTransfer: vi.fn().mockReturnValue(of(undefined)),
    };
    TestBed.configureTestingModule({ providers: [{ provide: ApiService, useValue: api }] });
    const page = TestBed.createComponent(TransactionsPage).componentInstance;
    page.ngOnInit();
    return page;
  }

  describe('carga y fusión de movimientos', () => {
    it('ngOnInit carga cuentas, categorías y la página de movimientos', () => {
      const page = create();
      expect(page.accounts).toEqual([acc1, acc2]);
      expect(page.categories).toEqual(categories);
      expect(page.movements.length).toBe(4);
    });

    it('conserva el orden que devuelve la API (fecha desc., a igualdad id desc.)', () => {
      const page = create();
      expect(page.movements.map(m => m.id)).toEqual([2, 3, 10, 1]);
    });

    it('load pasa los filtros actuales tanto a getTransactions como a getMovements', () => {
      const page = create();
      api.getTransactions.mockClear();
      api.getMovements.mockClear();
      page.filterCategoryId = catExpense.id!;
      page.filterFrom = '2026-01-01';
      page.load();
      expect(api.getTransactions).toHaveBeenCalledWith('2026-01-01', undefined, undefined, catExpense.id);
      expect(api.getMovements).toHaveBeenCalledWith('2026-01-01', undefined, undefined, catExpense.id, 0, 25);
    });

    it('onFiltersChange resetea a la primera página antes de recargar', () => {
      const page = create();
      page.page = 3;
      api.getMovements.mockClear();
      page.onFiltersChange();
      expect(page.page).toBe(0);
      expect(api.getMovements).toHaveBeenCalledWith(undefined, undefined, undefined, undefined, 0, 25);
    });

    it('onPageChange cambia de página y solo recarga los movimientos (no las devoluciones)', () => {
      const page = create();
      api.getTransactions.mockClear();
      api.getMovements.mockClear();
      page.onPageChange(2);
      expect(page.page).toBe(2);
      expect(api.getMovements).toHaveBeenCalledWith(undefined, undefined, undefined, undefined, 2, 25);
      expect(api.getTransactions).not.toHaveBeenCalled();
    });

    it('onSizeChange cambia el tamaño de página y recarga', () => {
      const page = create();
      api.getMovements.mockClear();
      page.onSizeChange(100);
      expect(page.size).toBe(100);
      expect(api.getMovements).toHaveBeenCalledWith(undefined, undefined, undefined, undefined, 0, 100);
    });

    it('vuelca el contenido y el total de elementos de la página en el estado', () => {
      const page = create();
      expect(page.totalElements).toBe(4);
    });
  });

  describe('cálculo de pendiente de devolución', () => {
    it('pendingFor descuenta lo ya devuelto', () => {
      const page = create();
      expect(page.pendingFor(txA)).toBe(70);
    });

    it('refundableExpenses excluye las propias devoluciones y las agotadas', () => {
      const page = create();
      expect(page.refundableExpenses.map(t => t.id)).toEqual([1]);
    });

    it('refundableExpenses incluye la agotada si es la que se está editando', () => {
      const fullyRefunded: Transaction = { ...txRefundOfA, id: 4, amount: 70 };
      const page = create([txA, txRefundOfA, fullyRefunded, txB]);
      page.refundOriginal = txA;
      expect(page.pendingFor(txA)).toBe(0);
      expect(page.refundableExpenses.map(t => t.id)).toContain(1);
    });
  });

  describe('categorías del formulario', () => {
    it('formCategories está vacío para una transferencia', () => {
      const page = create();
      page.kind = 'TRANSFER';
      expect(page.formCategories).toEqual([]);
    });

    it('formCategories filtra por tipo y por ámbito (global o de la cuenta elegida)', () => {
      const page = create();
      page.kind = 'EXPENSE';
      page.form.accountId = acc1.id!;
      expect(page.formCategories.map(c => c.id)).toEqual(
        expect.arrayContaining([catExpense.id, catExpenseSub.id, catExpenseBound.id]),
      );
      page.form.accountId = acc2.id!;
      expect(page.formCategories.map(c => c.id)).not.toContain(catExpenseBound.id);
    });

    it('formCategoryOptions ordena padre→hijo', () => {
      const page = create();
      page.kind = 'EXPENSE';
      page.form.accountId = acc1.id!;
      const opts = page.formCategoryOptions;
      const parentIdx = opts.findIndex(o => o.cat.id === catExpense.id);
      const childIdx = opts.findIndex(o => o.cat.id === catExpenseSub.id);
      expect(opts[parentIdx].child).toBe(false);
      expect(opts[childIdx].child).toBe(true);
      expect(childIdx).toBeGreaterThan(parentIdx);
    });

    it('scopeSuffix añade el nombre de la cuenta si la categoría está ligada', () => {
      const page = create();
      expect(page.scopeSuffix(catExpense)).toBe('');
      expect(page.scopeSuffix(catExpenseBound)).toBe(' · Corriente');
    });

    it('orderedCategories (para el filtro) también va padre→hijo, sin filtrar por tipo/ámbito', () => {
      const page = create();
      const opts = page.orderedCategories;
      expect(opts.map(o => o.cat.id)).toContain(catIncome.id);
      const parentIdx = opts.findIndex(o => o.cat.id === catExpense.id);
      const childIdx = opts.findIndex(o => o.cat.id === catExpenseSub.id);
      expect(childIdx).toBeGreaterThan(parentIdx);
    });
  });

  describe('abrir el formulario', () => {
    it('openNew resetea a un gasto nuevo con las cuentas por defecto', () => {
      const page = create();
      page.openNew();
      expect(page.kind).toBe('EXPENSE');
      expect(page.editingId).toBeNull();
      expect(page.refundOriginal).toBeNull();
      expect(page.fromAccountId).toBe(1);
      expect(page.toAccountId).toBe(2);
    });

    it('openRefund prepara una devolución del gasto indicado', () => {
      const page = create();
      const row = page.movements.find(m => m.id === 1)!;
      page.openRefund(row);
      expect(page.kind).toBe('REFUND');
      expect(page.refundOriginal).toBe(txA);
      expect(page.form.amount).toBe(70);
    });

    it('openEdit de un gasto normal precarga tipo, cuenta y categoría', () => {
      const page = create();
      const row = page.movements.find(m => m.id === 1)!;
      page.openEdit(row);
      expect(page.editingId).toBe(1);
      expect(page.editingSource).toBe('tx');
      expect(page.kind).toBe('EXPENSE');
      expect(page.form.categoryId).toBe(catExpense.id);
    });

    it('openEdit de una devolución detecta el gasto original', () => {
      const page = create();
      const row = page.movements.find(m => m.id === 2)!;
      page.openEdit(row);
      expect(page.kind).toBe('REFUND');
      expect(page.refundOriginal?.id).toBe(1);
    });

    it('openEdit de una transferencia precarga origen y destino', () => {
      const page = create();
      const row = page.movements.find(m => m.id === 10)!;
      page.openEdit(row);
      expect(page.editingId).toBe(10);
      expect(page.editingSource).toBe('tr');
      expect(page.kind).toBe('TRANSFER');
      expect(page.fromAccountId).toBe(1);
      expect(page.toAccountId).toBe(2);
    });

    it('onCancel limpia el error', () => {
      const page = create();
      page.error = 'algo';
      page.onCancel(new Event('cancel'));
      expect(page.error).toBe('');
    });
  });

  describe('onKindChange / sincronización', () => {
    it('al pasar a devolución, preselecciona un gasto pendiente y su importe', () => {
      const page = create();
      page.kind = 'REFUND';
      page.onKindChange();
      expect(page.refundOriginal?.id).toBe(1);
      expect(page.form.amount).toBe(70);
    });

    it('al dejar de ser devolución, limpia refundOriginal y ajusta la categoría', () => {
      const page = create();
      page.refundOriginal = txA;
      page.kind = 'EXPENSE';
      page.form.accountId = acc1.id!;
      page.form.categoryId = 999; // inválida
      page.onKindChange();
      expect(page.refundOriginal).toBeNull();
      expect(page.form.categoryId).toBe(page.formCategories[0]?.id ?? 0);
    });

    it('syncCategory no toca nada si es una transferencia', () => {
      const page = create();
      page.kind = 'TRANSFER';
      page.form.categoryId = 999;
      page.syncCategory();
      expect(page.form.categoryId).toBe(999);
    });
  });

  describe('save', () => {
    it('rechaza un importe no válido', () => {
      const page = create();
      page.form.amount = 'abc' as unknown as number;
      page.save();
      expect(page.error).toContain('no válido');
      expect(api.createTransaction).not.toHaveBeenCalled();
    });

    describe('devolución', () => {
      it('exige un gasto original seleccionado', () => {
        const page = create();
        page.kind = 'REFUND';
        page.refundOriginal = null;
        page.form.amount = 10;
        page.save();
        expect(page.error).toContain('Selecciona el gasto');
      });

      it('rechaza un importe mayor que el pendiente', () => {
        const page = create();
        page.kind = 'REFUND';
        page.refundOriginal = txA;
        page.form.amount = 999;
        page.save();
        expect(page.error).toContain('supera el importe pendiente');
      });

      it('crea la devolución con refundOfId', () => {
        const page = create();
        page.kind = 'REFUND';
        page.refundOriginal = txA;
        page.form.date = '2026-07-10';
        page.form.amount = 20;
        page.form.description = 'parcial';
        page.save();
        expect(api.createTransaction).toHaveBeenCalledWith({
          date: '2026-07-10', amount: 20, description: 'parcial', type: 'EXPENSE',
          accountId: acc1.id, categoryId: catExpense.id, refundOfId: 1,
        });
      });

      it('actualiza la devolución cuando se edita una existente', () => {
        const page = create();
        const row = page.movements.find(m => m.id === 2)!;
        page.openEdit(row);
        page.form.amount = 25;
        page.save();
        expect(api.updateTransaction).toHaveBeenCalledWith(2, expect.objectContaining({ refundOfId: 1 }));
      });
    });

    describe('transferencia', () => {
      it('rechaza origen y destino iguales', () => {
        const page = create();
        page.kind = 'TRANSFER';
        page.fromAccountId = 1;
        page.toAccountId = 1;
        page.form.amount = 10;
        page.save();
        expect(page.error).toContain('distintas');
      });

      it('crea la transferencia', () => {
        const page = create();
        page.kind = 'TRANSFER';
        page.fromAccountId = 1;
        page.toAccountId = 2;
        page.form.amount = 50;
        page.save();
        expect(api.createTransfer).toHaveBeenCalledWith(expect.objectContaining({
          fromAccountId: 1, toAccountId: 2, amount: 50,
        }));
      });

      it('convierte un movimiento normal en transferencia (borra y crea)', () => {
        const page = create();
        const row = page.movements.find(m => m.id === 1)!;
        page.openEdit(row);
        page.kind = 'TRANSFER';
        page.fromAccountId = 1;
        page.toAccountId = 2;
        page.form.amount = 50;
        page.save();
        expect(api.deleteTransaction).toHaveBeenCalledWith(1);
        expect(api.createTransfer).toHaveBeenCalled();
      });

      it('actualiza una transferencia existente', () => {
        const page = create();
        const row = page.movements.find(m => m.id === 10)!;
        page.openEdit(row);
        page.form.amount = 600;
        page.save();
        expect(api.updateTransfer).toHaveBeenCalledWith(10, expect.objectContaining({ amount: 600 }));
      });
    });

    describe('ingreso/gasto normal', () => {
      it('crea un gasto', () => {
        const page = create();
        page.kind = 'EXPENSE';
        page.form.amount = 40;
        page.form.accountId = 1;
        page.form.categoryId = catExpense.id!;
        page.save();
        expect(api.createTransaction).toHaveBeenCalledWith(expect.objectContaining({
          type: 'EXPENSE', accountId: 1, categoryId: catExpense.id,
        }));
      });

      it('convierte una transferencia en movimiento normal (borra y crea)', () => {
        const page = create();
        const row = page.movements.find(m => m.id === 10)!;
        page.openEdit(row);
        page.kind = 'EXPENSE';
        page.form.amount = 40;
        page.form.accountId = 1;
        page.form.categoryId = catExpense.id!;
        page.save();
        expect(api.deleteTransfer).toHaveBeenCalledWith(10);
        expect(api.createTransaction).toHaveBeenCalled();
      });

      it('actualiza un movimiento normal existente', () => {
        const page = create();
        const row = page.movements.find(m => m.id === 1)!;
        page.openEdit(row);
        page.form.amount = 45;
        page.save();
        expect(api.updateTransaction).toHaveBeenCalledWith(1, expect.objectContaining({ amount: 45 }));
      });
    });

    it('en éxito, cierra el formulario y recarga', () => {
      const page = create();
      api.getTransactions.mockClear();
      page.kind = 'EXPENSE';
      page.form.amount = 40;
      page.form.accountId = 1;
      page.form.categoryId = catExpense.id!;
      page.save();
      expect(api.getTransactions).toHaveBeenCalled();
    });

    it('en error, usa el detail/message de la API o el mensaje genérico', () => {
      const page = create();
      api.createTransaction.mockReturnValue(throwError(() => ({ error: { detail: 'boom' } })));
      page.kind = 'EXPENSE';
      page.form.amount = 40;
      page.save();
      expect(page.error).toBe('boom');
    });
  });

  describe('remove', () => {
    it('un movimiento normal sin devoluciones pide confirmación simple', () => {
      const confirmSpy = vi.spyOn(window, 'confirm').mockReturnValue(false);
      const page = create();
      const row = page.movements.find(m => m.id === 3)!;
      page.remove(row);
      expect(confirmSpy.mock.calls[0][0]).toContain('¿Eliminar el movimiento');
      expect(api.deleteTransaction).not.toHaveBeenCalled();
    });

    it('un gasto con devoluciones asociadas avisa de que se eliminan en cascada', () => {
      const confirmSpy = vi.spyOn(window, 'confirm').mockReturnValue(true);
      const page = create();
      const row = page.movements.find(m => m.id === 1)!;
      page.remove(row);
      expect(confirmSpy.mock.calls[0][0]).toContain('devoluciones asociadas');
      expect(api.deleteTransaction).toHaveBeenCalledWith(1);
    });

    it('una transferencia pide confirmación con las cuentas implicadas', () => {
      const confirmSpy = vi.spyOn(window, 'confirm').mockReturnValue(true);
      const page = create();
      const row = page.movements.find(m => m.id === 10)!;
      page.remove(row);
      expect(confirmSpy.mock.calls[0][0]).toBe('¿Eliminar la transferencia de Corriente a Ahorro?');
      expect(api.deleteTransfer).toHaveBeenCalledWith(10);
    });
  });
});
