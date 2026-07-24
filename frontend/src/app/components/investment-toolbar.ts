import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Output, ViewChild, inject, ChangeDetectionStrategy } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { InvestmentContextService } from '../investment-context.service';
import { InvestmentTransactionView } from '../models';
import { FlexImportDialog } from './flex-import-dialog';
import { InvestmentTransactionDialog } from './investment-transaction-dialog';

/**
 * Barra de herramientas compartida por Panel general y Operaciones (§7):
 * selector/alta de cartera + diálogos de alta manual e import Flex. El
 * estado de cartera vive en {@link InvestmentContextService} para que la
 * selección sobreviva al navegar entre ambas páginas.
 */
@Component({
  selector: 'app-investment-toolbar',
  imports: [CommonModule, FormsModule, FlexImportDialog, InvestmentTransactionDialog],
  template: `
    <div class="card toolbar">
      <div class="portfolio-nav">
        @if (ctx.portfolios.length > 0) {
          <label class="portfolio-filter">Cartera
            <select [(ngModel)]="ctx.portfolioId">
              @for (p of ctx.portfolios; track p.id) {
                <option [ngValue]="p.id">{{ p.name }} ({{ p.baseCurrency }})</option>
              }
            </select>
          </label>
        }
        @if (!ctx.creating) {
          <button class="btn small" (click)="ctx.startCreate()">Nueva cartera</button>
        } @else {
          <form class="create-form" (ngSubmit)="ctx.createPortfolio()">
            <input name="name" [(ngModel)]="ctx.newName" placeholder="Nombre de la cartera" required>
            <input name="currency" class="currency" [(ngModel)]="ctx.newCurrency" maxlength="3"
                   placeholder="EUR" required title="Divisa base (ISO 4217); inmutable tras la creación">
            <button class="btn small primary" type="submit" [disabled]="!ctx.newName.trim()">Crear</button>
            <button class="btn small" type="button" (click)="ctx.cancelCreate()">Cancelar</button>
          </form>
          @if (ctx.createError) {
            <span class="amount-expense">{{ ctx.createError }}</span>
          }
        }
      </div>
      @if (ctx.portfolioId != null) {
        <div class="toolbar-actions">
          <app-investment-transaction-dialog [portfolioId]="ctx.portfolioId"
                                             [baseCurrency]="ctx.baseCurrency" (done)="done.emit()" />
          <app-flex-import-dialog [portfolioId]="ctx.portfolioId" (done)="done.emit()" />
        </div>
      }
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './investment-toolbar.scss'
})
export class InvestmentToolbar {
  readonly ctx = inject(InvestmentContextService);

  @Output() done = new EventEmitter<void>();

  @ViewChild(InvestmentTransactionDialog) txDialog?: InvestmentTransactionDialog;

  edit(tx: InvestmentTransactionView): void {
    this.txDialog?.edit(tx);
  }
}
