
import { Component, EventEmitter, Input, OnInit, Output, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { ApiService } from '../api.service';
import {
  InvestmentSecurity, InvestmentTransactionRequest, InvestmentTransactionType,
  InvestmentTransactionView, INVESTMENT_TYPE_LABELS
} from '../models';

/**
 * Formulario de alta/edición manual de operaciones de cartera (RF-2, H2.3).
 * Muestra los campos condicionales según el tipo (§3: instrumento, cantidad,
 * pierna FX) con pistas del convenio de signos; los invariantes reales los
 * valida el dominio y llegan como 400 con detalle. La edición se abre desde el
 * listado (H2.4) vía {@link edit}.
 */
@Component({
  selector: 'app-investment-transaction-dialog',
  imports: [FormsModule],
  template: `
    <button class="btn primary" (click)="openNew()">{{ label }}</button>

    @if (visible) {
      <div class="overlay" (click)="close()">
        <div class="dialog card" (click)="$event.stopPropagation()">
          <h2>{{ editingId ? 'Editar operación' : 'Nueva operación' }}</h2>

          <form (ngSubmit)="save()">
            <div class="grid">
              <label>Tipo
                <select name="type" [(ngModel)]="type" required>
                  @for (t of types; track t) {
                    <option [ngValue]="t">{{ typeLabels[t] }}</option>
                  }
                </select>
              </label>
              <label>Fecha
                <input name="tradeDate" type="date" [(ngModel)]="tradeDate" required>
              </label>

              @if (securityRule(type) !== 'FORBIDDEN') {
                <label>Instrumento{{ securityRule(type) === 'OPTIONAL' ? ' (opcional)' : '' }}
                  <select name="securityId" [(ngModel)]="securityId">
                    @if (securityRule(type) === 'OPTIONAL') {
                      <option [ngValue]="null">—</option>
                    }
                    @for (s of securities; track s.id) {
                      <option [ngValue]="s.id">{{ s.name }} ({{ s.currency }})</option>
                    }
                  </select>
                </label>
              }

              @if (hasQuantity(type)) {
                <label>Títulos {{ quantityHint(type) }}
                  <input name="quantity" type="number" step="any" [(ngModel)]="quantity">
                </label>
                @if (type !== 'SPLIT') {
                  <label>Precio
                    <input name="price" type="number" step="any" min="0" [(ngModel)]="price">
                  </label>
                }
              }

              <label>Importe {{ amountHint(type) }}
                <input name="amount" type="number" step="any" [(ngModel)]="amount" required>
              </label>
              <label>Divisa
                <input name="currency" class="currency" maxlength="3" [(ngModel)]="currency" required>
              </label>

              @if (type === 'FX_TRADE') {
                <label>Importe entrante (+)
                  <input name="counterAmount" type="number" step="any" [(ngModel)]="counterAmount">
                </label>
                <label>Divisa entrante
                  <input name="counterCurrency" class="currency" maxlength="3" [(ngModel)]="counterCurrency">
                </label>
              }

              <label>Comisión (−, opcional)
                <input name="fee" type="number" step="any" [(ngModel)]="fee">
              </label>
              <label>Retención (−, opcional)
                <input name="tax" type="number" step="any" [(ngModel)]="tax">
              </label>
              <label>Tipo de cambio a base (opcional)
                <input name="fxRateToBase" type="number" step="any" min="0" [(ngModel)]="fxRateToBase">
              </label>
              <label class="wide">Descripción
                <input name="description" [(ngModel)]="description">
              </label>
            </div>

            @if (error) {
              <p class="amount-expense">{{ error }}</p>
            }

            <div class="dialog-actions">
              <button class="btn primary" type="submit" [disabled]="loading || !tradeDate || amount === null">
                {{ loading ? 'Guardando…' : 'Guardar' }}
              </button>
              <button class="btn" type="button" (click)="close()">Cancelar</button>
            </div>
          </form>
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
      width: min(680px, 94vw);
      max-height: 88vh;
      overflow-y: auto;

      h2 {
        font-size: 1.1rem;
        margin: 0 0 1rem;
        color: var(--text);
      }
    }

    .grid {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: .75rem 1rem;

      label {
        display: flex;
        flex-direction: column;
        gap: .3rem;
        font-size: .75rem;
        font-weight: 600;
        text-transform: uppercase;
        letter-spacing: .06em;
        color: var(--text-muted);

        &.wide {
          grid-column: 1 / -1;
        }
      }

      .currency {
        text-transform: uppercase;
      }
    }

    .dialog-actions {
      display: flex;
      gap: .5rem;
      justify-content: flex-end;
      margin-top: 1rem;
    }
  `
})
export class InvestmentTransactionDialog implements OnInit {
  private api = inject(ApiService);

  @Input({ required: true }) portfolioId!: number;
  @Input() baseCurrency = 'EUR';
  @Input() label = '+ Operación';
  @Output() done = new EventEmitter<void>();

  readonly types: InvestmentTransactionType[] = [
    'BUY', 'SELL', 'DIVIDEND', 'INTEREST', 'FEE', 'TAX', 'TRADE_TAX',
    'SPLIT', 'DEPOSIT', 'WITHDRAWAL', 'FX_TRADE'
  ];
  readonly typeLabels = INVESTMENT_TYPE_LABELS;

  visible = false;
  loading = false;
  error = '';
  securities: InvestmentSecurity[] = [];

  editingId: number | null = null;
  type: InvestmentTransactionType = 'BUY';
  tradeDate = '';
  securityId: number | null = null;
  quantity: number | null = null;
  price: number | null = null;
  amount: number | null = null;
  currency = 'EUR';
  counterAmount: number | null = null;
  counterCurrency = '';
  fee: number | null = null;
  tax: number | null = null;
  fxRateToBase: number | null = null;
  description = '';

  ngOnInit(): void {
    this.api.getSecurities().subscribe(s => this.securities = s);
  }

  securityRule(type: InvestmentTransactionType): 'REQUIRED' | 'OPTIONAL' | 'FORBIDDEN' {
    if (type === 'DEPOSIT' || type === 'WITHDRAWAL' || type === 'FX_TRADE') return 'FORBIDDEN';
    if (type === 'INTEREST' || type === 'FEE' || type === 'TAX') return 'OPTIONAL';
    return 'REQUIRED';
  }

  hasQuantity(type: InvestmentTransactionType): boolean {
    return type === 'BUY' || type === 'SELL' || type === 'SPLIT';
  }

  quantityHint(type: InvestmentTransactionType): string {
    if (type === 'BUY') return '(+)';
    if (type === 'SELL') return '(−)';
    return '(delta con signo)';
  }

  amountHint(type: InvestmentTransactionType): string {
    switch (type) {
      case 'BUY': case 'FEE': case 'TAX': case 'TRADE_TAX':
      case 'WITHDRAWAL': case 'FX_TRADE': return '(−)';
      case 'SPLIT': return '(0)';
      default: return '(+)';
    }
  }

  openNew(): void {
    this.reset();
    this.visible = true;
  }

  /** Abre el formulario precargado para editar una operación (desde el listado). */
  edit(tx: InvestmentTransactionView): void {
    this.reset();
    this.editingId = tx.id;
    this.type = tx.type;
    this.tradeDate = tx.tradeDate;
    this.securityId = tx.securityId;
    this.quantity = tx.quantity;
    this.price = tx.price;
    this.amount = tx.amount;
    this.currency = tx.currency;
    this.counterAmount = tx.counterAmount;
    this.counterCurrency = tx.counterCurrency ?? '';
    this.fee = tx.fee;
    this.tax = tx.tax;
    this.fxRateToBase = tx.fxRateToBase;
    this.description = tx.description ?? '';
    this.visible = true;
  }

  close(): void {
    this.visible = false;
  }

  save(): void {
    if (!this.tradeDate || this.amount === null) return;
    this.loading = true;
    this.error = '';
    const req: InvestmentTransactionRequest = {
      type: this.type,
      tradeDate: this.tradeDate,
      securityId: this.securityRule(this.type) === 'FORBIDDEN' ? null : this.securityId,
      quantity: this.hasQuantity(this.type) ? this.quantity : null,
      price: this.hasQuantity(this.type) && this.type !== 'SPLIT' ? this.price : null,
      amount: this.amount,
      currency: this.currency.trim().toUpperCase(),
      counterAmount: this.type === 'FX_TRADE' ? this.counterAmount : null,
      counterCurrency: this.type === 'FX_TRADE' && this.counterCurrency.trim()
        ? this.counterCurrency.trim().toUpperCase() : null,
      fee: this.fee,
      tax: this.tax,
      fxRateToBase: this.fxRateToBase,
      description: this.description.trim() || null
    };
    const obs = this.editingId
      ? this.api.updateInvestmentTransaction(this.editingId, req)
      : this.api.createInvestmentTransaction(this.portfolioId, req);
    obs.subscribe({
      next: () => {
        this.loading = false;
        this.visible = false;
        this.done.emit();
      },
      error: (e: HttpErrorResponse) => {
        this.loading = false;
        this.error = e.error?.detail ?? e.error?.message ?? 'No se pudo guardar la operación.';
      }
    });
  }

  private reset(): void {
    this.editingId = null;
    this.type = 'BUY';
    this.tradeDate = new Date().toISOString().slice(0, 10);
    this.securityId = null;
    this.quantity = null;
    this.price = null;
    this.amount = null;
    this.currency = this.baseCurrency;
    this.counterAmount = null;
    this.counterCurrency = '';
    this.fee = null;
    this.tax = null;
    this.fxRateToBase = null;
    this.description = '';
    this.error = '';
  }
}
