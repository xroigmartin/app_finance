import { CommonModule } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { ApiService } from '../../api.service';
import { parseAmount } from '../../amount';
import { ImportDialog } from '../../components/import-dialog';
import { Account, Transfer, TransferRequest } from '../../models';

@Component({
  selector: 'app-transfers',
  imports: [CommonModule, FormsModule, ImportDialog],
  templateUrl: './transfers.html',
  styleUrl: './transfers.scss'
})
export class TransfersPage implements OnInit {
  private api = inject(ApiService);

  transfers: Transfer[] = [];
  accounts: Account[] = [];

  showForm = false;
  editingId: number | null = null;
  error = '';
  form: TransferRequest = this.emptyForm();

  ngOnInit(): void {
    this.api.getAccounts().subscribe(a => this.accounts = a);
    this.load();
  }

  load(): void {
    this.api.getTransfers().subscribe(t => this.transfers = t);
  }

  openNew(): void {
    this.editingId = null;
    this.form = this.emptyForm();
    this.showForm = true;
    this.error = '';
  }

  openEdit(t: Transfer): void {
    this.editingId = t.id!;
    this.form = {
      date: t.date,
      amount: t.amount,
      description: t.description,
      fromAccountId: t.fromAccount.id!,
      toAccountId: t.toAccount.id!
    };
    this.showForm = true;
    this.error = '';
  }

  save(): void {
    if (this.form.fromAccountId === this.form.toAccountId) {
      this.error = 'La cuenta de origen y la de destino deben ser distintas.';
      return;
    }
    const amount = parseAmount(this.form.amount);
    if (isNaN(amount) || amount <= 0) {
      this.error = 'Importe no válido. Usa coma o punto para los decimales (ej.: 1234,56).';
      return;
    }
    const req = { ...this.form, amount };
    const obs = this.editingId
      ? this.api.updateTransfer(this.editingId, req)
      : this.api.createTransfer(req);
    obs.subscribe({
      next: () => {
        this.showForm = false;
        this.load();
      },
      error: (e: HttpErrorResponse) =>
        this.error = e.error?.detail ?? e.error?.message ?? 'Error al guardar la transferencia.'
    });
  }

  remove(t: Transfer): void {
    if (!confirm(`¿Eliminar la transferencia de ${t.fromAccount.name} a ${t.toAccount.name}?`)) return;
    this.api.deleteTransfer(t.id!).subscribe(() => this.load());
  }

  private emptyForm(): TransferRequest {
    return {
      date: new Date().toISOString().slice(0, 10),
      amount: 0,
      description: '',
      fromAccountId: this.accounts[0]?.id ?? 0,
      toAccountId: this.accounts[1]?.id ?? 0
    };
  }
}
