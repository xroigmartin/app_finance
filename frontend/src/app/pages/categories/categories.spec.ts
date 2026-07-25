import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { ApiService } from '../../api.service';
import { Account, Category, CategoryRule, RecurringBudget } from '../../models';
import { CategoriesPage } from './categories';

describe('CategoriesPage', () => {
  afterEach(() => vi.restoreAllMocks());

  let api: {
    getAccounts: ReturnType<typeof vi.fn>;
    getCategories: ReturnType<typeof vi.fn>;
    getCategoryRules: ReturnType<typeof vi.fn>;
    createCategory: ReturnType<typeof vi.fn>;
    updateCategory: ReturnType<typeof vi.fn>;
    deleteCategory: ReturnType<typeof vi.fn>;
    getRecurrence: ReturnType<typeof vi.fn>;
    saveRecurrence: ReturnType<typeof vi.fn>;
    deleteRecurrence: ReturnType<typeof vi.fn>;
    createCategoryRule: ReturnType<typeof vi.fn>;
    updateCategoryRule: ReturnType<typeof vi.fn>;
    deleteCategoryRule: ReturnType<typeof vi.fn>;
  };

  const account: Account = { id: 1, name: 'Corriente', type: 'Banco', initialBalance: 0 };

  const nomina: Category = { id: 10, name: 'Nómina', type: 'INCOME', color: '#0a0' };
  const otrosIngresos: Category = { id: 11, name: 'Otros ingresos', type: 'INCOME', color: '#0b0' };
  const alimentacion: Category = { id: 20, name: 'Alimentación', type: 'EXPENSE', color: '#a00' };
  const vivienda: Category = { id: 21, name: 'Vivienda', type: 'EXPENSE', color: '#b00' };
  const superSub: Category = {
    id: 22, name: 'Supermercado', type: 'EXPENSE', color: '#a00', parent: alimentacion,
  };
  const bankCategory: Category = { id: 30, name: 'Propia de cuenta', type: 'EXPENSE', color: '#c00', account };

  const categories: Category[] = [nomina, otrosIngresos, alimentacion, vivienda, superSub, bankCategory];
  const rule: CategoryRule = { id: 1, pattern: 'MERCADONA', category: alimentacion };

  function create(cats: Category[] = categories): CategoriesPage {
    api = {
      getAccounts: vi.fn().mockReturnValue(of([account])),
      getCategories: vi.fn().mockReturnValue(of(cats)),
      getCategoryRules: vi.fn().mockReturnValue(of([rule])),
      createCategory: vi.fn().mockReturnValue(of({ id: 99, ...({} as Category) })),
      updateCategory: vi.fn().mockReturnValue(of({ id: 20 })),
      deleteCategory: vi.fn().mockReturnValue(of(undefined)),
      getRecurrence: vi.fn().mockReturnValue(of(null)),
      saveRecurrence: vi.fn().mockReturnValue(of({})),
      deleteRecurrence: vi.fn().mockReturnValue(of(null)),
      createCategoryRule: vi.fn().mockReturnValue(of({ rule, recategorized: 0 })),
      updateCategoryRule: vi.fn().mockReturnValue(of({ rule, recategorized: 0 })),
      deleteCategoryRule: vi.fn().mockReturnValue(of(undefined)),
    };
    TestBed.configureTestingModule({ providers: [{ provide: ApiService, useValue: api }] });
    const page = TestBed.createComponent(CategoriesPage).componentInstance;
    page.ngOnInit();
    return page;
  }

  it('ngOnInit carga cuentas, categorías y reglas', () => {
    const page = create();
    expect(page.accounts).toEqual([account]);
    expect(page.categories).toEqual(categories);
    expect(page.rules).toEqual([rule]);
  });

  describe('listados derivados', () => {
    it('incomeCategories/expenseCategories son las de primer nivel, alfabéticas', () => {
      const page = create();
      expect(page.incomeCategories.map(c => c.name)).toEqual(['Nómina', 'Otros ingresos']);
      expect(page.expenseCategories.map(c => c.name)).toEqual(['Alimentación', 'Propia de cuenta', 'Vivienda']);
    });

    it('childrenOf devuelve las subcategorías de un padre, alfabéticas', () => {
      const page = create();
      expect(page.childrenOf(alimentacion.id).map(c => c.name)).toEqual(['Supermercado']);
      expect(page.childrenOf(vivienda.id)).toEqual([]);
    });

    it('parentOptions excluye la categoría que se está editando', () => {
      const page = create();
      page.editingId = alimentacion.id!;
      expect(page.parentOptions.map(c => c.id)).not.toContain(alimentacion.id);
      expect(page.parentOptions.map(c => c.id)).toContain(vivienda.id);
    });

    it('orderedCategories intercala gastos e ingresos, cada padre seguido de sus hijos', () => {
      const page = create();
      const flat = page.orderedCategories;
      const idx = (id: number) => flat.findIndex(e => e.cat.id === id);
      expect(idx(alimentacion.id!)).toBeLessThan(idx(superSub.id!));
      expect(flat.find(e => e.cat.id === superSub.id)?.child).toBe(true);
      expect(flat.find(e => e.cat.id === alimentacion.id)?.child).toBe(false);
      expect(idx(vivienda.id!)).toBeGreaterThan(idx(alimentacion.id!)); // gastos primero
      expect(idx(nomina.id!)).toBeGreaterThan(flat.findIndex(e => e.cat.type === 'EXPENSE'));
    });
  });

  describe('ámbito y jerarquía del formulario', () => {
    it('isSubcategory refleja si hay un padre elegido', () => {
      const page = create();
      expect(page.isSubcategory).toBe(false);
      page.formParentId = alimentacion.id!;
      expect(page.isSubcategory).toBe(true);
    });

    it('parentIsGlobal es true solo si el padre elegido no tiene cuenta', () => {
      const page = create();
      page.formParentId = alimentacion.id!;
      expect(page.parentIsGlobal).toBe(true);
      page.formParentId = bankCategory.id!;
      expect(page.parentIsGlobal).toBe(false);
      page.formParentId = null;
      expect(page.parentIsGlobal).toBe(false);
    });

    it('scopeLocked solo bloquea subcategorías de un padre ligado a cuenta', () => {
      const page = create();
      page.formParentId = bankCategory.id!;
      expect(page.scopeLocked).toBe(true);
      page.formParentId = alimentacion.id!;
      expect(page.scopeLocked).toBe(false);
      page.formParentId = null;
      expect(page.scopeLocked).toBe(false);
    });

    it('onParentChange hereda el tipo, y el ámbito solo si el padre está ligado a cuenta', () => {
      const page = create();
      page.formParentId = bankCategory.id!;
      page.formAccountId = null;
      page.onParentChange();
      expect(page.form.type).toBe('EXPENSE');
      expect(page.formAccountId).toBe(1);

      page.formParentId = alimentacion.id!;
      page.formAccountId = 5;
      page.onParentChange();
      expect(page.formAccountId).toBe(5); // no se toca: el padre es global
    });

    it('scopeLabel devuelve el nombre de la cuenta o "Global"', () => {
      const page = create();
      expect(page.scopeLabel(alimentacion)).toBe('Global');
      expect(page.scopeLabel(bankCategory)).toBe('Corriente');
    });
  });

  describe('recurrencia', () => {
    it('isLeafForRecurrence es true sin categoría en edición o sin subcategorías', () => {
      const page = create();
      expect(page.isLeafForRecurrence).toBe(true);
      page.editingId = vivienda.id!;
      expect(page.isLeafForRecurrence).toBe(true);
      page.editingId = alimentacion.id!;
      expect(page.isLeafForRecurrence).toBe(false);
    });

    it('canHaveRecurrence exige cuenta y ser hoja', () => {
      const page = create();
      page.formAccountId = null;
      expect(page.canHaveRecurrence).toBe(false);
      page.formAccountId = 1;
      expect(page.canHaveRecurrence).toBe(true);
      page.editingId = alimentacion.id!;
      expect(page.canHaveRecurrence).toBe(false);
    });

    it('toggleMonth alterna el mes indicado', () => {
      const page = create();
      expect(page.recMonths[0]).toBe(false);
      page.toggleMonth(0);
      expect(page.recMonths[0]).toBe(true);
      page.toggleMonth(0);
      expect(page.recMonths[0]).toBe(false);
    });

    it('addAmount/removeAmount añaden y quitan tramos', () => {
      const page = create();
      page.addAmount();
      expect(page.recAmounts.length).toBe(1);
      expect(page.recAmounts[0].amount).toBe(0);
      page.removeAmount(0);
      expect(page.recAmounts.length).toBe(0);
    });

    it('openEdit carga la recurrencia existente cuando la categoría puede tenerla', () => {
      const existing: RecurringBudget = {
        months: [1, 3], active: false, amounts: [{ id: 5, amount: 100, validoDesde: '2026-01' }],
      };
      api = { ...api };
      const page = create();
      api.getRecurrence.mockReturnValue(of(existing));
      page.openEdit(bankCategory);
      expect(page.recExisting).toBe(true);
      expect(page.recActive).toBe(false);
      expect(page.recMonths[0]).toBe(true);
      expect(page.recMonths[2]).toBe(true);
      expect(page.recMonths[1]).toBe(false);
      expect(page.recAmounts).toEqual(existing.amounts);
    });

    it('openEdit no toca la recurrencia si la categoría no puede tenerla (global)', () => {
      const page = create();
      page.openEdit(alimentacion);
      expect(api.getRecurrence).not.toHaveBeenCalled();
      expect(page.recExisting).toBe(false);
    });

    it('openEdit sin recurrencia existente deja recExisting en false', () => {
      const page = create();
      page.openEdit(bankCategory);
      expect(api.getRecurrence).toHaveBeenCalledWith(bankCategory.id);
      expect(page.recExisting).toBe(false);
    });
  });

  describe('openNew / openNewSub / openEdit', () => {
    it('openNew resetea formulario, ámbito, jerarquía y recurrencia', () => {
      const page = create();
      page.editingId = 5;
      page.formAccountId = 1;
      page.formParentId = 1;
      page.recMonths[0] = true;
      page.openNew();
      expect(page.editingId).toBeNull();
      expect(page.formAccountId).toBeNull();
      expect(page.formParentId).toBeNull();
      expect(page.recMonths.every(m => !m)).toBe(true);
      expect(page.form).toEqual({ name: '', type: 'EXPENSE', color: '#44618e' });
    });

    it('openNewSub hereda tipo y color del padre y fija formParentId/formAccountId', () => {
      const page = create();
      page.openNewSub(bankCategory);
      expect(page.form.type).toBe(bankCategory.type);
      expect(page.form.color).toBe(bankCategory.color);
      expect(page.formParentId).toBe(bankCategory.id);
      expect(page.formAccountId).toBe(account.id);
    });

    it('openEdit precarga el formulario, ámbito y jerarquía', () => {
      const page = create();
      page.openEdit(superSub);
      expect(page.editingId).toBe(superSub.id);
      expect(page.form).toEqual(superSub);
      expect(page.formParentId).toBe(alimentacion.id);
    });
  });

  describe('save', () => {
    it('crea sin ámbito ni padre (global, top-level)', () => {
      const page = create();
      page.form = { name: 'Nueva', type: 'EXPENSE', color: '#000' };
      page.formAccountId = null;
      page.formParentId = null;
      page.save();
      expect(api.createCategory).toHaveBeenCalledWith(
        expect.objectContaining({ name: 'Nueva', account: null, parent: null }),
      );
    });

    it('actualiza cuando hay editingId, con ámbito y padre', () => {
      const page = create();
      page.openEdit(superSub);
      page.formAccountId = 1;
      page.save();
      expect(api.updateCategory).toHaveBeenCalledWith(superSub.id, expect.objectContaining({
        account: { id: 1 }, parent: { id: alimentacion.id },
      }));
    });

    it('sin recurrencia deseada, no borra ni guarda ninguna', () => {
      const page = create();
      page.form = { name: 'x', type: 'EXPENSE', color: '#000' };
      page.formAccountId = null;
      page.save();
      expect(api.deleteRecurrence).not.toHaveBeenCalled();
      expect(api.saveRecurrence).not.toHaveBeenCalled();
    });

    it('con recurrencia deseada (cuenta + al menos un mes y un importe), la guarda tras crear', () => {
      const page = create();
      api.createCategory.mockReturnValue(of({ id: 55 }));
      page.form = { name: 'x', type: 'EXPENSE', color: '#000' };
      page.formAccountId = 1;
      page.recMonths[0] = true;
      page.recAmounts = [{ amount: 100, validoDesde: '2026-01' }];
      page.save();
      expect(api.saveRecurrence).toHaveBeenCalledWith(55, { months: [1], active: true, amounts: page.recAmounts });
    });

    it('si existía recurrencia y deja de quererse, la borra antes de guardar la categoría', () => {
      const page = create();
      page.openEdit(bankCategory);
      page.recExisting = true; // simula que ya tenía una recurrencia cargada
      page.recMonths = new Array(12).fill(false); // ya no hay meses -> no se quiere recurrencia
      page.recAmounts = [];
      page.save();
      expect(api.deleteRecurrence).toHaveBeenCalledWith(bankCategory.id);
      expect(api.saveRecurrence).not.toHaveBeenCalled();
    });

    it('en éxito, cierra el formulario y recarga', () => {
      const page = create();
      page.form = { name: 'x', type: 'EXPENSE', color: '#000' };
      page.formAccountId = null;
      api.getCategories.mockClear();
      page.save();
      expect(api.getCategories).toHaveBeenCalled();
    });

    it('un fallo al guardar la categoría usa el mensaje de saveErrorMessage', () => {
      const page = create();
      api.createCategory.mockReturnValue(throwError(() => ({ error: { detail: 'nombre duplicado' } })));
      page.form = { name: 'x', type: 'EXPENSE', color: '#000' };
      page.formAccountId = null;
      page.save();
      expect(page.error).toBe('nombre duplicado');
    });

    it('un fallo al guardar la recurrencia usa su propio mensaje, no el de categoría', () => {
      const page = create();
      api.createCategory.mockReturnValue(of({ id: 55 }));
      api.saveRecurrence.mockReturnValue(throwError(() => ({ error: {} })));
      page.form = { name: 'x', type: 'EXPENSE', color: '#000' };
      page.formAccountId = 1;
      page.recMonths[0] = true;
      page.recAmounts = [{ amount: 100, validoDesde: '2026-01' }];
      page.save();
      expect(page.error).toBe('Error al guardar la recurrencia del pago previsto.');
    });

    it('un fallo al borrar la recurrencia previa también usa el mensaje de recurrencia', () => {
      const page = create();
      page.openEdit(bankCategory);
      page.recExisting = true;
      page.recMonths = new Array(12).fill(false);
      page.recAmounts = [];
      api.deleteRecurrence.mockReturnValue(throwError(() => ({ error: {} })));
      page.save();
      expect(page.error).toBe('Error al guardar la recurrencia del pago previsto.');
      expect(api.updateCategory).not.toHaveBeenCalled();
    });
  });

  describe('remove', () => {
    it('no borra si no se confirma', () => {
      vi.spyOn(window, 'confirm').mockReturnValue(false);
      const page = create();
      page.remove(alimentacion);
      expect(api.deleteCategory).not.toHaveBeenCalled();
    });

    it('borra y recarga si se confirma', () => {
      vi.spyOn(window, 'confirm').mockReturnValue(true);
      const page = create();
      page.remove(alimentacion);
      expect(api.deleteCategory).toHaveBeenCalledWith(alimentacion.id);
    });

    it('en conflicto (409), avisa con el detalle de la API o el mensaje por defecto', () => {
      vi.spyOn(window, 'confirm').mockReturnValue(true);
      vi.spyOn(window, 'alert').mockImplementation(() => {});
      const page = create();
      api.deleteCategory.mockReturnValue(throwError(() => ({ status: 409, error: { detail: 'tiene movimientos' } })));
      page.remove(alimentacion);
      expect(window.alert).toHaveBeenCalledWith('tiene movimientos');

      api.deleteCategory.mockReturnValue(throwError(() => ({ status: 409, error: {} })));
      page.remove(alimentacion);
      expect(window.alert)
        .toHaveBeenCalledWith('La categoría tiene elementos asociados y no se puede eliminar.');
    });

    it('en otro error, avisa con el mensaje genérico', () => {
      vi.spyOn(window, 'confirm').mockReturnValue(true);
      vi.spyOn(window, 'alert').mockImplementation(() => {});
      const page = create();
      api.deleteCategory.mockReturnValue(throwError(() => ({ status: 500 })));
      page.remove(alimentacion);
      expect(window.alert).toHaveBeenCalledWith('Error al eliminar la categoría.');
    });
  });

  describe('reglas de categorización', () => {
    it('openNewRule preselecciona la primera categoría', () => {
      const page = create();
      page.openNewRule();
      expect(page.editingRuleId).toBeNull();
      expect(page.ruleForm).toEqual({ pattern: '', categoryId: categories[0].id });
    });

    it('openEditRule precarga el patrón y la categoría de la regla', () => {
      const page = create();
      page.openEditRule(rule);
      expect(page.editingRuleId).toBe(rule.id);
      expect(page.ruleForm).toEqual({ pattern: rule.pattern, categoryId: rule.category.id });
    });

    it('saveRule informa de cuántos movimientos se recategorizaron', () => {
      const page = create();
      api.createCategoryRule.mockReturnValue(of({ rule, recategorized: 3 }));
      page.saveRule();
      expect(page.ruleInfo).toContain('3 movimientos recategorizados');
    });

    it('saveRule informa de que ninguno coincidía si recategorized es 0', () => {
      const page = create();
      api.createCategoryRule.mockReturnValue(of({ rule, recategorized: 0 }));
      page.saveRule();
      expect(page.ruleInfo).toContain('Ningún movimiento');
    });

    it('saveRule actualiza cuando hay editingRuleId', () => {
      const page = create();
      page.openEditRule(rule);
      page.saveRule();
      expect(api.updateCategoryRule).toHaveBeenCalledWith(rule.id, page.ruleForm);
    });

    it('en error, fija ruleError', () => {
      const page = create();
      api.createCategoryRule.mockReturnValue(throwError(() => new Error('down')));
      page.saveRule();
      expect(page.ruleError).toBe('Error al guardar la regla.');
    });

    it('removeRule no borra sin confirmar y borra confirmando', () => {
      vi.spyOn(window, 'confirm').mockReturnValue(false);
      const page = create();
      page.removeRule(rule);
      expect(api.deleteCategoryRule).not.toHaveBeenCalled();

      (window.confirm as ReturnType<typeof vi.fn>).mockReturnValue(true);
      page.removeRule(rule);
      expect(api.deleteCategoryRule).toHaveBeenCalledWith(rule.id);
    });
  });
});
