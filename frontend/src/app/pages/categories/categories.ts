
import { Component, ElementRef, OnInit, inject, viewChild, ChangeDetectionStrategy } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { Observable, of, throwError } from 'rxjs';
import { catchError, switchMap } from 'rxjs/operators';
import { ApiService } from '../../api.service';
import { Account, Category, CategoryRule, RecurringAmount, RecurringBudget, RuleRequest } from '../../models';

@Component({
  selector: 'app-categories',
  imports: [FormsModule],
  templateUrl: './categories.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './categories.scss'
})
export class CategoriesPage implements OnInit {
  private api = inject(ApiService);

  /** Native modals hosting the forms; opened on demand to avoid scrolling. */
  private catDialog = viewChild<ElementRef<HTMLDialogElement>>('catDialog');
  private ruleDialog = viewChild<ElementRef<HTMLDialogElement>>('ruleDialog');

  categories: Category[] = [];
  accounts: Account[] = [];
  editingId: number | null = null;
  error = '';
  form: Category = this.emptyForm();
  /** Owning account of the form's category; null means global. */
  formAccountId: number | null = null;
  /** Parent category of the form; null means a top-level category. */
  formParentId: number | null = null;

  // --- Recurrence section (only for leaf, account-bound categories) ---
  readonly monthLabels = ['Ene', 'Feb', 'Mar', 'Abr', 'May', 'Jun', 'Jul', 'Ago', 'Sep', 'Oct', 'Nov', 'Dic'];
  /** Selected months, index 0 = January … 11 = December. */
  recMonths: boolean[] = new Array(12).fill(false);
  recActive = true;
  recAmounts: RecurringAmount[] = [];
  /** Whether a recurrence already existed when the form was opened. */
  recExisting = false;

  rules: CategoryRule[] = [];
  editingRuleId: number | null = null;
  ruleError = '';
  ruleInfo = '';
  ruleForm: RuleRequest = { pattern: '', categoryId: 0 };

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.api.getAccounts().subscribe(a => this.accounts = a);
    this.api.getCategories().subscribe(c => this.categories = c);
    this.api.getCategoryRules().subscribe(r => this.rules = r);
  }

  /** Top-level income categories (subcategories are listed under their parent). */
  get incomeCategories(): Category[] {
    return this.topLevel('INCOME');
  }

  get expenseCategories(): Category[] {
    return this.topLevel('EXPENSE');
  }

  private topLevel(type: 'INCOME' | 'EXPENSE'): Category[] {
    return this.categories
      .filter(c => c.type === type && !c.parent)
      .sort((a, b) => a.name.localeCompare(b.name));
  }

  /** Subcategories of a given parent, alphabetically. */
  childrenOf(parentId?: number): Category[] {
    return this.categories
      .filter(c => c.parent?.id === parentId)
      .sort((a, b) => a.name.localeCompare(b.name));
  }

  /** Top-level categories selectable as a parent (excludes the one being edited). */
  get parentOptions(): Category[] {
    return this.categories
      .filter(c => !c.parent && c.id !== this.editingId)
      .sort((a, b) => a.name.localeCompare(b.name));
  }

  /** Flat category list in parent→child order, for the rule dropdown. */
  get orderedCategories(): { cat: Category; child: boolean }[] {
    const out: { cat: Category; child: boolean }[] = [];
    for (const type of ['EXPENSE', 'INCOME'] as const) {
      for (const top of this.topLevel(type)) {
        out.push({ cat: top, child: false });
        for (const ch of this.childrenOf(top.id)) {
          out.push({ cat: ch, child: true });
        }
      }
    }
    return out;
  }

  get isSubcategory(): boolean {
    return this.formParentId !== null;
  }

  /** True when the form's chosen parent is a global category. */
  get parentIsGlobal(): boolean {
    if (this.formParentId === null) return false;
    const p = this.categories.find(c => c.id === this.formParentId);
    return !!p && !p.account;
  }

  /**
   * The scope selector is locked only for subcategories of an account-bound
   * parent (they inherit it). Top-level categories and subcategories of a
   * global parent can choose their scope.
   */
  get scopeLocked(): boolean {
    return this.isSubcategory && !this.parentIsGlobal;
  }

  /** When a parent is chosen, type is inherited; scope only if it is account-bound. */
  onParentChange(): void {
    const p = this.categories.find(c => c.id === this.formParentId);
    if (p) {
      this.form.type = p.type;
      if (p.account) {
        this.formAccountId = p.account.id ?? null;
      }
    }
  }

  /** "Global" or the owning account's name. */
  scopeLabel(c: Category): string {
    return c.account ? c.account.name : 'Global';
  }

  // --- Recurrence ---

  /** A recurrence is only allowed on a leaf category bound to an account. */
  get isLeafForRecurrence(): boolean {
    return this.editingId === null || this.childrenOf(this.editingId).length === 0;
  }

  get canHaveRecurrence(): boolean {
    return this.formAccountId !== null && this.isLeafForRecurrence;
  }

  private currentYearMonth(): string {
    const now = new Date();
    return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`;
  }

  toggleMonth(i: number): void {
    this.recMonths[i] = !this.recMonths[i];
  }

  addAmount(): void {
    this.recAmounts.push({ amount: 0, validoDesde: this.currentYearMonth() });
  }

  removeAmount(i: number): void {
    this.recAmounts.splice(i, 1);
  }

  /** True when the form holds a usable recurrence (at least one month and amount). */
  private hasRecurrenceInput(): boolean {
    return this.recMonths.some(m => m) && this.recAmounts.length > 0;
  }

  private resetRecurrence(): void {
    this.recMonths = new Array(12).fill(false);
    this.recActive = true;
    this.recAmounts = [];
    this.recExisting = false;
  }

  private loadRecurrence(categoryId: number): void {
    this.api.getRecurrence(categoryId).subscribe(r => {
      if (!r) return;
      this.recExisting = true;
      this.recActive = r.active;
      this.recMonths = new Array(12).fill(false);
      for (const m of r.months) this.recMonths[m - 1] = true;
      this.recAmounts = r.amounts.map(a => ({ ...a }));
    });
  }

  private buildRecurrencePayload(): RecurringBudget {
    const months: number[] = [];
    this.recMonths.forEach((on, i) => { if (on) months.push(i + 1); });
    return { months, active: this.recActive, amounts: this.recAmounts };
  }

  openNew(): void {
    this.editingId = null;
    this.form = this.emptyForm();
    this.formAccountId = null;
    this.formParentId = null;
    this.resetRecurrence();
    this.error = '';
    this.openForm();
  }

  /** Open/close the modal forms. Native dialog gives focus-trap and Escape. */
  private openForm(): void {
    this.catDialog()?.nativeElement.showModal();
  }

  closeForm(): void {
    this.catDialog()?.nativeElement.close();
  }

  onCatCancel(_event: Event): void {
    this.error = '';
  }

  /** Opens the form to add a subcategory under the given parent. */
  openNewSub(parent: Category): void {
    this.editingId = null;
    this.form = this.emptyForm();
    this.form.type = parent.type;
    this.form.color = parent.color;
    this.formParentId = parent.id ?? null;
    this.formAccountId = parent.account?.id ?? null;
    this.resetRecurrence();
    this.error = '';
    this.openForm();
  }

  openEdit(c: Category): void {
    this.editingId = c.id!;
    this.form = { ...c };
    this.formAccountId = c.account?.id ?? null;
    this.formParentId = c.parent?.id ?? null;
    this.resetRecurrence();
    if (this.canHaveRecurrence) {
      this.loadRecurrence(c.id!);
    }
    this.error = '';
    this.openForm();
  }

  save(): void {
    const payload: Category = {
      ...this.form,
      account: this.formAccountId ? ({ id: this.formAccountId } as Account) : null,
      parent: this.formParentId ? ({ id: this.formParentId } as Category) : null
    };
    const wantsRecurrence = this.canHaveRecurrence && this.hasRecurrenceInput();

    // If a recurrence existed but is no longer wanted (e.g. the scope was set
    // to global), remove it first so the category update is not rejected.
    const pre: Observable<unknown> =
      this.editingId && this.recExisting && !wantsRecurrence
        ? this.api.deleteRecurrence(this.editingId)
        : of(null);

    // Each stage maps its own error to a message so that, e.g., a recurrence
    // failure is never reported as a duplicate category name.
    pre
      .pipe(
        catchError((e: HttpErrorResponse) => throwError(() => this.recurrenceErrorMessage(e))),
        switchMap(() =>
          (this.editingId
            ? this.api.updateCategory(this.editingId, payload)
            : this.api.createCategory(payload)
          ).pipe(catchError((e: HttpErrorResponse) => throwError(() => this.saveErrorMessage(e))))
        ),
        switchMap((saved: Category) =>
          wantsRecurrence && saved.id
            ? this.api.saveRecurrence(saved.id, this.buildRecurrencePayload())
                .pipe(catchError((e: HttpErrorResponse) => throwError(() => this.recurrenceErrorMessage(e))))
            : of(null)
        )
      )
      .subscribe({
        next: () => {
          this.closeForm();
          this.load();
        },
        error: (message: string) => (this.error = message)
      });
  }

  private saveErrorMessage(e: HttpErrorResponse): string {
    return e.error?.detail ?? e.error?.message ?? 'Error al guardar la categoría.';
  }

  private recurrenceErrorMessage(e: HttpErrorResponse): string {
    return e.error?.detail ?? e.error?.message ?? 'Error al guardar la recurrencia del pago previsto.';
  }

  remove(c: Category): void {
    if (!confirm(`¿Eliminar la categoría "${c.name}"?`)) return;
    this.api.deleteCategory(c.id!).subscribe({
      next: () => this.load(),
      error: (e: HttpErrorResponse) =>
        alert(e.status === 409
          ? (e.error?.detail ?? e.error?.message
            ?? 'La categoría tiene elementos asociados y no se puede eliminar.')
          : 'Error al eliminar la categoría.')
    });
  }

  private emptyForm(): Category {
    return { name: '', type: 'EXPENSE', color: '#44618e' };
  }

  openNewRule(): void {
    this.editingRuleId = null;
    this.ruleForm = { pattern: '', categoryId: this.categories[0]?.id ?? 0 };
    this.ruleError = '';
    this.ruleInfo = '';
    this.openRuleForm();
  }

  openEditRule(rule: CategoryRule): void {
    this.editingRuleId = rule.id!;
    this.ruleForm = { pattern: rule.pattern, categoryId: rule.category.id! };
    this.ruleError = '';
    this.ruleInfo = '';
    this.openRuleForm();
  }

  private openRuleForm(): void {
    this.ruleDialog()?.nativeElement.showModal();
  }

  closeRuleForm(): void {
    this.ruleDialog()?.nativeElement.close();
  }

  onRuleCancel(_event: Event): void {
    this.ruleError = '';
  }

  saveRule(): void {
    const obs = this.editingRuleId
      ? this.api.updateCategoryRule(this.editingRuleId, this.ruleForm)
      : this.api.createCategoryRule(this.ruleForm);
    obs.subscribe({
      next: result => {
        this.closeRuleForm();
        this.ruleInfo = result.recategorized > 0
          ? `Regla guardada: ${result.recategorized} movimientos recategorizados de "Otros gastos/ingresos" a "${result.rule.category.name}".`
          : 'Regla guardada. Ningún movimiento de "Otros gastos/ingresos" coincide con el patrón.';
        this.load();
      },
      error: () => this.ruleError = 'Error al guardar la regla.'
    });
  }

  removeRule(rule: CategoryRule): void {
    if (!confirm(`¿Eliminar la regla "${rule.pattern}"?`)) return;
    this.api.deleteCategoryRule(rule.id!).subscribe(() => this.load());
  }
}
