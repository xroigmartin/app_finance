import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, OnInit, Output, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { ApiService } from '../api.service';
import { Account, ImportResult } from '../models';

@Component({
  selector: 'app-import-dialog',
  imports: [CommonModule, FormsModule],
  template: `
    <button class="btn" (click)="open()">{{ label }}</button>

    @if (visible) {
      <div class="overlay" (click)="close()">
        <div class="dialog card" (click)="$event.stopPropagation()">
          <h2>{{ kind === 'transactions' ? 'Importar movimientos' : 'Importar transferencias' }}</h2>

          @if (kind === 'transactions') {
            <p class="muted hint">
              Acepta exportaciones del banco (.xls, .xlsx o CSV). Solo necesita columnas
              <code>fecha</code> e <code>importe</code> (negativo = gasto). La categoría se asigna
              con tus reglas de categorización; sin coincidencia va a "Otros gastos/ingresos".
            </p>
            <label class="field">1. Cuenta donde importar los movimientos
              <select [(ngModel)]="accountId">
                @for (a of accounts; track a.id) {
                  <option [ngValue]="a.id">{{ a.name }}</option>
                }
              </select>
            </label>
          } @else {
            <p class="muted hint">
              El fichero (.xls, .xlsx o CSV) necesita columnas <code>fecha</code>,
              <code>importe</code>, <code>origen</code> y <code>destino</code> con los nombres
              de tus cuentas, y opcionalmente <code>descripcion</code>.
            </p>
          }

          <label class="field">{{ kind === 'transactions' ? '2. ' : '' }}Documento a importar
            <div class="file-zone" (click)="fileInput.click()">
              @if (file) {
                <strong>{{ file.name }}</strong>
              } @else {
                <span class="muted">Haz clic para elegir un fichero .csv, .xls o .xlsx</span>
              }
            </div>
          </label>
          <input #fileInput type="file" accept=".csv,.txt,.xlsx,.xls" hidden
                 (change)="onFile(fileInput)">

          @if (error) {
            <p class="amount-expense">{{ error }}</p>
          }
          @if (result) {
            <div class="result">
              <p>
                <strong>{{ result.imported }}</strong> filas importadas.
                @if (result.duplicated > 0) {
                  <span class="muted">{{ result.duplicated }} omitidas por estar ya registradas.</span>
                }
                @if (result.errors.length > 0) {
                  <span class="amount-expense">{{ result.errors.length }} con errores:</span>
                }
              </p>
              @if (result.errors.length > 0) {
                <ul class="errors">
                  @for (e of result.errors; track e.row) {
                    <li>Fila {{ e.row }}: {{ e.message }}</li>
                  }
                </ul>
              }
            </div>
          }

          <div class="dialog-actions">
            <button class="btn primary" (click)="doImport()"
                    [disabled]="loading || !file || (kind === 'transactions' && !accountId)">
              {{ loading ? 'Importando…' : 'Importar' }}
            </button>
            <button class="btn" (click)="close()">{{ result ? 'Cerrar' : 'Cancelar' }}</button>
          </div>
        </div>
      </div>
    }
  `,
  styles: `
    .overlay {
      position: fixed;
      inset: 0;
      background: rgba(15, 23, 42, .45);
      display: flex;
      align-items: center;
      justify-content: center;
      z-index: 100;
    }

    .dialog {
      width: min(560px, 92vw);
      max-height: 85vh;
      overflow-y: auto;

      h2 {
        font-size: 1.1rem;
        margin: 0 0 .75rem;
        color: var(--text);
      }
    }

    .hint {
      font-size: .85rem;
      margin: 0 0 1rem;

      code {
        background: var(--bg);
        border: 1px solid var(--border);
        padding: .1rem .3rem;
        border-radius: 2px;
      }
    }

    .field {
      display: flex;
      flex-direction: column;
      gap: .35rem;
      font-size: .85rem;
      font-weight: 600;
      color: var(--text-muted);
      margin-bottom: 1rem;

      select {
        max-width: 280px;
      }
    }

    .file-zone {
      border: 1px dashed var(--border-strong);
      border-radius: 2px;
      padding: 1.25rem;
      text-align: center;
      cursor: pointer;
      font-weight: 400;

      &:hover {
        border-color: var(--accent);
        background: var(--bg);
      }
    }

    .result p {
      margin: 0 0 .5rem;
    }

    .errors {
      margin: 0 0 .75rem;
      padding-left: 1.2rem;
      max-height: 160px;
      overflow-y: auto;
      font-size: .85rem;
      color: var(--neg);
    }

    .dialog-actions {
      display: flex;
      gap: .5rem;
      justify-content: flex-end;
      margin-top: .5rem;
    }
  `
})
export class ImportDialog implements OnInit {
  private api = inject(ApiService);

  @Input({ required: true }) kind!: 'transactions' | 'transfers';
  @Input() label = 'Importar CSV/Excel';
  @Output() done = new EventEmitter<void>();

  visible = false;
  loading = false;
  accounts: Account[] = [];
  accountId?: number;
  file: File | null = null;
  result: ImportResult | null = null;
  error = '';

  ngOnInit(): void {
    if (this.kind === 'transactions') {
      this.api.getAccounts().subscribe(a => {
        this.accounts = a;
        this.accountId ??= a[0]?.id;
      });
    }
  }

  open(): void {
    this.visible = true;
    this.file = null;
    this.result = null;
    this.error = '';
  }

  close(): void {
    this.visible = false;
  }

  onFile(input: HTMLInputElement): void {
    this.file = input.files?.[0] ?? null;
    input.value = '';
    this.result = null;
    this.error = '';
  }

  doImport(): void {
    if (!this.file) return;
    this.loading = true;
    this.result = null;
    this.error = '';
    const obs = this.kind === 'transactions'
      ? this.api.importTransactions(this.file, this.accountId)
      : this.api.importTransfers(this.file);
    obs.subscribe({
      next: result => {
        this.loading = false;
        this.result = result;
        this.file = null;
        if (result.imported > 0) this.done.emit();
      },
      error: (e: HttpErrorResponse) => {
        this.loading = false;
        this.error = e.error?.detail ?? e.error?.message ?? 'Error al importar el fichero.';
      }
    });
  }
}
