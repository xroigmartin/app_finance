import { CommonModule } from '@angular/common';
import { Component, OnInit, inject, ChangeDetectionStrategy } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../api.service';
import { parseAmount } from '../../amount';
import { Account, AnnualBudget, AnnualRow, BudgetRequest } from '../../models';

/** Planned + real totals for the 12 months plus the annual figure. */
interface Series {
  budget: number[];
  actual: number[];
  totalBudget: number;
  totalActual: number;
}

@Component({
  selector: 'app-budgets',
  imports: [CommonModule, FormsModule],
  templateUrl: './budgets.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './budgets.scss'
})
export class BudgetsPage implements OnInit {
  private api = inject(ApiService);

  year = new Date().getFullYear();

  accounts: Account[] = [];
  /** Selected account; null aggregates every account but disables editing. */
  accountId: number | null = null;

  data: AnnualBudget | null = null;
  error = '';

  // Derived series, recomputed whenever the matrix changes.
  income: Series = this.emptySeries();
  expense: Series = this.emptySeries();
  savings: Series = this.emptySeries();
  /** Running savings totals (AHORRO ACUMULADO). */
  cumulative: Series = this.emptySeries();

  readonly months = [
    'Enero', 'Febrero', 'Marzo', 'Abril', 'Mayo', 'Junio',
    'Julio', 'Agosto', 'Septiembre', 'Octubre', 'Noviembre', 'Diciembre'
  ];
  readonly monthsShort = [
    'Ene', 'Feb', 'Mar', 'Abr', 'May', 'Jun',
    'Jul', 'Ago', 'Sep', 'Oct', 'Nov', 'Dic'
  ];
  readonly monthIndexes = Array.from({ length: 12 }, (_, i) => i);

  ngOnInit(): void {
    this.api.getAccounts().subscribe(a => {
      this.accounts = a;
      this.accountId = a[0]?.id ?? null;
      this.load();
    });
  }

  get editable(): boolean {
    return this.accountId !== null;
  }

  get selectedAccountName(): string {
    return this.accounts.find(a => a.id === this.accountId)?.name ?? 'Todas las cuentas';
  }

  prevYear(): void { this.year--; this.load(); }
  nextYear(): void { this.year++; this.load(); }

  onAccountChange(): void { this.load(); }

  load(): void {
    this.error = '';
    const account = this.accountId ?? undefined;
    this.api.getAnnualBudget(this.year, account).subscribe({
      next: d => { this.data = d; this.recompute(); },
      error: () => { this.error = 'No se pudo cargar el presupuesto anual.'; }
    });
  }

  diff(actual: number, budget: number): number {
    return actual - budget;
  }

  /**
   * Colour for a difference: spending less than planned or earning more than
   * planned is good (green); the opposite is bad (red).
   */
  diffClass(diff: number, type: 'INCOME' | 'EXPENSE'): string {
    if (diff === 0) return '';
    const good = type === 'INCOME' ? diff > 0 : diff < 0;
    return good ? 'amount-income' : 'amount-expense';
  }

  /** Savings rate for a month/total: savings over income, or null without income. */
  rate(savings: number, income: number): number | null {
    return income > 0 ? savings / income : null;
  }

  /** Money formatter that blanks out zeros to keep the grid readable. */
  fmt(v: number): string {
    return v ? v.toLocaleString('es-ES', { minimumFractionDigits: 0, maximumFractionDigits: 2 }) : '';
  }

  pct(v: number | null): string {
    return v === null ? '' : (v * 100).toLocaleString('es-ES',
      { minimumFractionDigits: 0, maximumFractionDigits: 1 }) + '%';
  }

  /**
   * Persists a budget cell edit, reusing the per-month budget endpoints.
   * An empty or zero value removes the budget; reloads to keep totals exact.
   */
  onCellEdit(row: AnnualRow, monthIndex: number, event: Event): void {
    if (!this.editable) return;
    const input = event.target as HTMLInputElement;
    const raw = input.value.trim();
    const cell = row.months[monthIndex];
    const amount = raw === '' ? 0 : parseAmount(raw);

    if (isNaN(amount) || amount < 0) {
      this.error = 'Importe no válido. Usa coma o punto para los decimales (ej.: 1234,56).';
      this.load();
      return;
    }
    this.error = '';

    if (amount === 0) {
      if (cell.budgetId != null) {
        this.api.deleteBudget(cell.budgetId).subscribe(() => this.load());
      }
      return;
    }

    const req: BudgetRequest = {
      accountId: this.accountId!,
      categoryId: row.categoryId,
      year: this.year,
      month: monthIndex + 1,
      amount
    };
    const obs = cell.budgetId != null
      ? this.api.updateBudget(cell.budgetId, req)
      : this.api.createBudget(req);
    obs.subscribe({
      next: () => this.load(),
      error: () => { this.error = 'Error al guardar el presupuesto.'; this.load(); }
    });
  }

  private recompute(): void {
    if (!this.data) return;
    this.income = this.totals(this.data.income);
    this.expense = this.totals(this.data.expense);

    const savings = this.emptySeries();
    const cumulative = this.emptySeries();
    let runBudget = 0;
    let runActual = 0;
    for (let m = 0; m < 12; m++) {
      savings.budget[m] = this.income.budget[m] - this.expense.budget[m];
      savings.actual[m] = this.income.actual[m] - this.expense.actual[m];
      runBudget += savings.budget[m];
      runActual += savings.actual[m];
      cumulative.budget[m] = runBudget;
      cumulative.actual[m] = runActual;
    }
    savings.totalBudget = this.income.totalBudget - this.expense.totalBudget;
    savings.totalActual = this.income.totalActual - this.expense.totalActual;
    cumulative.totalBudget = runBudget;
    cumulative.totalActual = runActual;
    this.savings = savings;
    this.cumulative = cumulative;
  }

  private totals(rows: AnnualRow[]): Series {
    const s = this.emptySeries();
    for (const row of rows) {
      for (let m = 0; m < 12; m++) {
        s.budget[m] += row.months[m].budget;
        s.actual[m] += row.months[m].actual;
      }
    }
    s.totalBudget = s.budget.reduce((a, b) => a + b, 0);
    s.totalActual = s.actual.reduce((a, b) => a + b, 0);
    return s;
  }

  private emptySeries(): Series {
    return {
      budget: Array(12).fill(0),
      actual: Array(12).fill(0),
      totalBudget: 0,
      totalActual: 0
    };
  }
}
