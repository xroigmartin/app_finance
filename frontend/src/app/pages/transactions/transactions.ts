import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, ElementRef, OnInit, inject, viewChild, ChangeDetectionStrategy } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Observable, forkJoin, of } from 'rxjs';
import { ApiService } from '../../api.service';
import { parseAmount } from '../../amount';
import { ImportDialog } from '../../components/import-dialog';
import {
  Account, Category, Transaction, TransactionRequest, TransactionType,
  Transfer, TransferRequest
} from '../../models';

/** A movement is an income/expense transaction, a transfer or a refund of an expense. */
type MovementKind = TransactionType | 'TRANSFER' | 'REFUND';

interface MovementRow {
  source: 'tx' | 'tr';
  date: string;
  id: number;
  tx?: Transaction;
  tr?: Transfer;
}

@Component({
  selector: 'app-transactions',
  imports: [CommonModule, FormsModule, ImportDialog],
  templateUrl: './transactions.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './transactions.scss'
})
export class TransactionsPage implements OnInit {
  private api = inject(ApiService);

  /** Native modal hosting the create/edit form; opened on demand to avoid scrolling. */
  private editDialog = viewChild<ElementRef<HTMLDialogElement>>('editDialog');

  movements: MovementRow[] = [];
  accounts: Account[] = [];
  categories: Category[] = [];

  filterFrom = '';
  filterTo = '';
  filterAccountId: number | null = null;
  filterCategoryId: number | null = null;

  /** Id and source of the movement being edited; null when creating. */
  editingId: number | null = null;
  editingSource: 'tx' | 'tr' | null = null;
  error = '';

  kind: MovementKind = 'EXPENSE';
  form: TransactionRequest = this.emptyForm();
  fromAccountId = 0;
  toAccountId = 0;
  /** The expense being refunded, when kind === 'REFUND'. */
  refundOriginal: Transaction | null = null;

  ngOnInit(): void {
    this.api.getAccounts().subscribe(a => this.accounts = a);
    this.api.getCategories().subscribe(c => this.categories = c);
    this.load();
  }

  load(): void {
    const from = this.filterFrom || undefined;
    const to = this.filterTo || undefined;
    const account = this.filterAccountId ?? undefined;
    const tx$ = this.api.getTransactions(from, to, account, this.filterCategoryId ?? undefined);
    // Transfers have no category, so a category filter hides them.
    const tr$ = this.filterCategoryId ? of([] as Transfer[]) : this.api.getTransfers(from, to, account);
    forkJoin({ tx: tx$, tr: tr$ }).subscribe(({ tx, tr }) => this.movements = this.merge(tx, tr));
  }

  private merge(tx: Transaction[], tr: Transfer[]): MovementRow[] {
    const rows: MovementRow[] = [
      ...tx.map(t => ({ source: 'tx' as const, date: t.date, id: t.id!, tx: t })),
      ...tr.map(t => ({ source: 'tr' as const, date: t.date, id: t.id!, tr: t }))
    ];
    return rows.sort((a, b) => b.date.localeCompare(a.date) || b.id - a.id);
  }

  get isTransfer(): boolean {
    return this.kind === 'TRANSFER';
  }

  get isRefund(): boolean {
    return this.kind === 'REFUND';
  }

  /** Loaded transaction by id, to resolve an original with its computed pending. */
  private findTx(id: number): Transaction | null {
    return this.movements.find(m => m.source === 'tx' && m.tx!.id === id)?.tx ?? null;
  }

  /** Amount already refunded for an original, excluding the refund being edited. */
  private refundedSoFar(originalId: number, excludeId: number | null): number {
    return this.movements
      .filter(m => m.source === 'tx' && m.tx!.refundOf?.id === originalId && m.tx!.id !== excludeId)
      .reduce((sum, m) => sum + m.tx!.amount, 0);
  }

  /** Amount of an expense still available to refund (best-effort; the server caps it). */
  pendingFor(original: Transaction): number {
    return original.amount - this.refundedSoFar(original.id!, this.editingId);
  }

  /** Expenses (not refunds themselves) with a pending amount, for the refund picker. */
  get refundableExpenses(): Transaction[] {
    return this.movements
      .filter(m => m.source === 'tx' && m.tx!.type === 'EXPENSE' && !m.tx!.refundOf)
      .map(m => m.tx!)
      .filter(t => this.pendingFor(t) > 0 || t.id === this.refundOriginal?.id);
  }

  /** Categories valid for the form: matching type and global or of the chosen account. */
  get formCategories(): Category[] {
    if (this.isTransfer) {
      return [];
    }
    return this.categories.filter(c =>
      c.type === this.kind &&
      (!c.account || c.account.id === this.form.accountId));
  }

  /** Valid categories ordered parent→child, for an indented dropdown. */
  get formCategoryOptions(): { cat: Category; child: boolean }[] {
    const valid = this.formCategories;
    const tops = valid.filter(c => !c.parent).sort((a, b) => a.name.localeCompare(b.name));
    const out: { cat: Category; child: boolean }[] = [];
    for (const t of tops) {
      out.push({ cat: t, child: false });
      for (const ch of valid.filter(c => c.parent?.id === t.id).sort((a, b) => a.name.localeCompare(b.name))) {
        out.push({ cat: ch, child: true });
      }
    }
    return out;
  }

  scopeSuffix(c: Category): string {
    return c.account ? ' · ' + c.account.name : '';
  }

  /** All categories ordered parent→child, for the (exact-match) filter dropdown. */
  get orderedCategories(): { cat: Category; child: boolean }[] {
    const out: { cat: Category; child: boolean }[] = [];
    const tops = this.categories.filter(c => !c.parent).sort((a, b) => a.name.localeCompare(b.name));
    for (const t of tops) {
      out.push({ cat: t, child: false });
      for (const ch of this.categories.filter(c => c.parent?.id === t.id).sort((a, b) => a.name.localeCompare(b.name))) {
        out.push({ cat: ch, child: true });
      }
    }
    return out;
  }

  openNew(): void {
    this.editingId = null;
    this.editingSource = null;
    this.kind = 'EXPENSE';
    this.form = this.emptyForm();
    this.refundOriginal = null;
    this.fromAccountId = this.accounts[0]?.id ?? 0;
    this.toAccountId = this.accounts[1]?.id ?? 0;
    this.error = '';
    this.openForm();
  }

  /** Opens the form to register a refund of the given expense. */
  openRefund(row: MovementRow): void {
    this.error = '';
    this.editingId = null;
    this.editingSource = null;
    this.kind = 'REFUND';
    this.refundOriginal = row.tx!;
    this.form = this.emptyForm();
    this.form.amount = this.pendingFor(row.tx!);
    this.openForm();
  }

  openEdit(row: MovementRow): void {
    this.error = '';
    if (row.source === 'tx') {
      const t = row.tx!;
      this.editingId = t.id!;
      this.editingSource = 'tx';
      if (t.refundOf) {
        this.kind = 'REFUND';
        this.refundOriginal = this.findTx(t.refundOf.id!) ?? t.refundOf;
      } else {
        this.kind = t.type;
        this.refundOriginal = null;
      }
      this.form = {
        date: t.date,
        amount: t.amount,
        description: t.description,
        type: t.type,
        accountId: t.account.id!,
        categoryId: t.category.id!
      };
      this.fromAccountId = t.account.id!;
      this.toAccountId = this.accounts.find(a => a.id !== t.account.id)?.id ?? 0;
    } else {
      const t = row.tr!;
      this.editingId = t.id!;
      this.editingSource = 'tr';
      this.kind = 'TRANSFER';
      this.refundOriginal = null;
      this.form = this.emptyForm();
      this.form.date = t.date;
      this.form.amount = t.amount;
      this.form.description = t.description;
      this.fromAccountId = t.fromAccount.id!;
      this.toAccountId = t.toAccount.id!;
    }
    this.openForm();
  }

  /** Open/close the modal form. Uses the native dialog for focus-trapping and Escape. */
  private openForm(): void {
    this.editDialog()?.nativeElement.showModal();
  }

  closeForm(): void {
    this.editDialog()?.nativeElement.close();
  }

  /** Reset the error when the dialog is dismissed with Escape. */
  onCancel(_event: Event): void {
    this.error = '';
  }

  /** React to a change of movement type in the form. */
  onKindChange(): void {
    if (this.isRefund) {
      this.syncRefund();
    } else {
      this.refundOriginal = null;
      this.syncCategory();
    }
  }

  /** Default the refunded expense and preload the pending amount. */
  syncRefund(): void {
    const list = this.refundableExpenses;
    if (!this.refundOriginal || !list.some(t => t.id === this.refundOriginal!.id)) {
      this.refundOriginal = list[0] ?? null;
    }
    this.onRefundOriginalChange();
  }

  onRefundOriginalChange(): void {
    if (this.refundOriginal) {
      this.form.amount = this.pendingFor(this.refundOriginal);
    }
  }

  /** Keep the selected category valid when the type or account changes. */
  syncCategory(): void {
    if (this.isTransfer) {
      return;
    }
    if (!this.formCategories.some(c => c.id === this.form.categoryId)) {
      this.form.categoryId = this.formCategories[0]?.id ?? 0;
    }
  }

  save(): void {
    const amount = parseAmount(this.form.amount);
    if (isNaN(amount) || amount <= 0) {
      this.error = 'Importe no válido. Usa coma o punto para los decimales (ej.: 1234,56).';
      return;
    }

    if (this.isRefund) {
      if (!this.refundOriginal) {
        this.error = 'Selecciona el gasto que se devuelve.';
        return;
      }
      const pending = this.pendingFor(this.refundOriginal);
      if (amount > pending) {
        this.error = `La devolución supera el importe pendiente del gasto (pendiente: ${pending}).`;
        return;
      }
      const req: TransactionRequest = {
        date: this.form.date,
        amount,
        description: this.form.description,
        type: 'EXPENSE',
        accountId: this.refundOriginal.account.id!,
        categoryId: this.refundOriginal.category.id!,
        refundOfId: this.refundOriginal.id!
      };
      this.persist(this.editingSource === 'tx' && this.editingId !== null
        ? this.api.updateTransaction(this.editingId, req)
        : this.api.createTransaction(req));
      return;
    }

    if (this.isTransfer) {
      if (this.fromAccountId === this.toAccountId) {
        this.error = 'La cuenta de origen y la de destino deben ser distintas.';
        return;
      }
      const req: TransferRequest = {
        date: this.form.date,
        amount,
        description: this.form.description,
        fromAccountId: this.fromAccountId,
        toAccountId: this.toAccountId
      };
      // Convert from transaction to transfer by replacing the old record.
      if (this.editingSource === 'tx' && this.editingId !== null) {
        this.api.deleteTransaction(this.editingId)
          .subscribe(() => this.persist(this.api.createTransfer(req)));
        return;
      }
      this.persist(this.editingSource === 'tr' && this.editingId !== null
        ? this.api.updateTransfer(this.editingId, req)
        : this.api.createTransfer(req));
      return;
    }

    const req: TransactionRequest = {
      date: this.form.date,
      amount,
      description: this.form.description,
      type: this.kind as TransactionType,
      accountId: this.form.accountId,
      categoryId: this.form.categoryId
    };
    // Convert from transfer to transaction by replacing the old record.
    if (this.editingSource === 'tr' && this.editingId !== null) {
      this.api.deleteTransfer(this.editingId)
        .subscribe(() => this.persist(this.api.createTransaction(req)));
      return;
    }
    this.persist(this.editingSource === 'tx' && this.editingId !== null
      ? this.api.updateTransaction(this.editingId, req)
      : this.api.createTransaction(req));
  }

  private persist(obs: Observable<unknown>): void {
    obs.subscribe({
      next: () => {
        this.closeForm();
        this.load();
      },
      error: (e: HttpErrorResponse) =>
        this.error = e.error?.detail ?? e.error?.message ?? 'Error al guardar el movimiento.'
    });
  }

  remove(row: MovementRow): void {
    if (row.source === 'tx') {
      const t = row.tx!;
      const label = t.description ?? t.category.name;
      const hasRefunds = !t.refundOf && this.refundedSoFar(t.id!, null) > 0;
      const message = hasRefunds
        ? `Este gasto tiene devoluciones asociadas que también se eliminarán. ¿Eliminar "${label}" y sus devoluciones?`
        : `¿Eliminar el movimiento "${label}"?`;
      if (!confirm(message)) return;
      this.api.deleteTransaction(t.id!).subscribe(() => this.load());
    } else {
      const t = row.tr!;
      if (!confirm(`¿Eliminar la transferencia de ${t.fromAccount.name} a ${t.toAccount.name}?`)) return;
      this.api.deleteTransfer(t.id!).subscribe(() => this.load());
    }
  }

  private emptyForm(): TransactionRequest {
    return {
      date: new Date().toISOString().slice(0, 10),
      amount: 0,
      description: '',
      type: 'EXPENSE' as TransactionType,
      accountId: this.accounts[0]?.id ?? 0,
      categoryId: 0
    };
  }
}
