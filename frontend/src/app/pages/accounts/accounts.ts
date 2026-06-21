import { CommonModule } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { ApiService } from '../../api.service';
import { parseAmount } from '../../amount';
import { Account } from '../../models';

@Component({
  selector: 'app-accounts',
  imports: [CommonModule, FormsModule],
  templateUrl: './accounts.html',
  styleUrl: './accounts.scss'
})
export class AccountsPage implements OnInit {
  private api = inject(ApiService);

  accounts: Account[] = [];
  showForm = false;
  editingId: number | null = null;
  error = '';
  form: Account = this.emptyForm();

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.api.getAccounts().subscribe(a => this.accounts = a);
  }

  openNew(): void {
    this.editingId = null;
    this.form = this.emptyForm();
    this.showForm = true;
    this.error = '';
  }

  openEdit(a: Account): void {
    this.editingId = a.id!;
    this.form = { ...a };
    this.showForm = true;
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
        this.showForm = false;
        this.load();
      },
      error: (e: HttpErrorResponse) => this.error = e.error?.detail ?? e.error?.message ?? 'Error al guardar'
    });
  }

  remove(a: Account): void {
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
