import { CommonModule } from '@angular/common';
import { Component, ElementRef, OnInit, ViewChild, inject, ChangeDetectionStrategy } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { ApiService } from '../../api.service';
import { parseAmount } from '../../amount';
import { Account, Summary } from '../../models';

/** Grupo de cuentas por tipo (sistema de diseño): etiqueta, color/soft y si es pasivo. */
interface GroupDef {
  type: string;
  label: string;
  color: string;
  soft: string;
  liability?: boolean;
}

const GROUP_DEFS: GroupDef[] = [
  { type: 'Banco', label: 'Cuentas corrientes', color: 'var(--accent)', soft: 'var(--accent-soft)' },
  { type: 'Efectivo', label: 'Efectivo', color: 'var(--pos)', soft: 'var(--pos-soft)' },
  { type: 'Inversión', label: 'Inversión', color: 'var(--warn)', soft: 'var(--warn-soft)' },
  { type: 'Otro', label: 'Otras cuentas', color: 'var(--text-faint)', soft: 'var(--surface-2)' },
  { type: 'Tarjeta', label: 'Tarjetas y crédito', color: 'var(--neg)', soft: 'var(--neg-soft)', liability: true }
];

interface AccountRow {
  account: Account;
  /** Saldo actual (inicial + movimientos) del resumen de reporting; null si no está disponible. */
  balance: number | null;
}

interface Group extends GroupDef {
  rows: AccountRow[];
  total: number;
  /** Peso del grupo en la barra de distribución de activos (0–100). */
  pct: number;
}

@Component({
  selector: 'app-accounts',
  imports: [CommonModule, FormsModule],
  templateUrl: './accounts.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './accounts.scss'
})
export class AccountsPage implements OnInit {
  private api = inject(ApiService);

  @ViewChild('editDialog') editDialog?: ElementRef<HTMLDialogElement>;

  accounts: Account[] = [];
  summary: Summary | null = null;
  groups: Group[] = [];
  netWorth = 0;
  totalAssets = 0;
  totalLiabilities = 0;
  deltaPct: number | null = null;

  editingId: number | null = null;
  error = '';
  form: Account = this.emptyForm();

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    forkJoin({
      accounts: this.api.getAccounts(),
      // El saldo actual por cuenta sale del read-side de reporting (mes en curso);
      // si falla, la página degrada a mostrar solo el CRUD sin saldos ni hero.
      summary: this.api.getSummary().pipe(catchError(() => of(null)))
    }).subscribe(({ accounts, summary }) => {
      this.accounts = accounts;
      this.summary = summary;
      this.rebuild();
    });
  }

  private rebuild(): void {
    const balances = new Map<number, number>(
      (this.summary?.accounts ?? []).map(a => [a.id, a.balance])
    );
    const groups: Group[] = GROUP_DEFS.map(def => {
      const rows = this.accounts
        .filter(a => a.type === def.type)
        .map(a => ({ account: a, balance: balances.get(a.id!) ?? null }));
      const total = rows.reduce((sum, r) => sum + (r.balance ?? 0), 0);
      return { ...def, rows, total, pct: 0 };
    }).filter(g => g.rows.length > 0);

    this.totalAssets = groups.filter(g => !g.liability).reduce((s, g) => s + g.total, 0);
    this.totalLiabilities = groups.filter(g => g.liability).reduce((s, g) => s + g.total, 0);
    this.netWorth = this.totalAssets + this.totalLiabilities;
    this.deltaPct = this.summary?.monthGrowthPct ?? null;

    const positive = groups.filter(g => !g.liability && g.total > 0);
    const positiveTotal = positive.reduce((s, g) => s + g.total, 0);
    for (const g of positive) {
      g.pct = positiveTotal > 0 ? (g.total / positiveTotal) * 100 : 0;
    }
    this.groups = groups;
  }

  /** Grupos de activo con saldo positivo, para la barra de distribución y su leyenda. */
  get distribution(): Group[] {
    return this.groups.filter(g => !g.liability && g.pct > 0);
  }

  openNew(): void {
    this.editingId = null;
    this.form = this.emptyForm();
    this.error = '';
    this.editDialog?.nativeElement.showModal();
  }

  openEdit(a: Account): void {
    this.editingId = a.id!;
    this.form = { ...a };
    this.error = '';
    this.editDialog?.nativeElement.showModal();
  }

  closeForm(): void {
    this.error = '';
    this.editDialog?.nativeElement.close();
  }

  onCancel(_e: Event): void {
    this.error = '';
  }

  save(): void {
    const initialBalance = parseAmount(this.form.initialBalance);
    if (isNaN(initialBalance)) {
      this.error = 'Saldo inicial no válido. Usa coma o punto para los decimales (ej.: 1234,56).';
      return;
    }
    const account = { ...this.form, initialBalance };
    const obs = this.editingId
      ? this.api.updateAccount(this.editingId, account)
      : this.api.createAccount(account);
    obs.subscribe({
      next: () => {
        this.closeForm();
        this.load();
      },
      error: (e: HttpErrorResponse) => this.error = e.error?.detail ?? e.error?.message ?? 'Error al guardar'
    });
  }

  remove(a: Account, event?: Event): void {
    event?.stopPropagation();
    if (!confirm(`¿Eliminar la cuenta "${a.name}"?`)) return;
    this.api.deleteAccount(a.id!).subscribe({
      next: () => this.load(),
      error: (e: HttpErrorResponse) =>
        alert(e.status === 409
          ? 'La cuenta tiene movimientos asociados y no se puede eliminar.'
          : 'Error al eliminar la cuenta.')
    });
  }

  private emptyForm(): Account {
    return { name: '', type: 'Banco', initialBalance: 0 };
  }
}
