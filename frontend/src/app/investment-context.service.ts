import { Injectable, inject } from '@angular/core';
import { BehaviorSubject } from 'rxjs';
import { ApiService } from './api.service';
import { InvestmentSecurity, Portfolio } from './models';

/**
 * Estado de cartera compartido entre las páginas de inversión (Panel general
 * y Operaciones) y la barra de herramientas: qué carteras hay, cuál está
 * seleccionada y el alta inline de una nueva. `providedIn: 'root'` para que
 * la cartera elegida sobreviva al navegar entre ambas páginas.
 */
@Injectable({ providedIn: 'root' })
export class InvestmentContextService {
  private api = inject(ApiService);

  portfolios: Portfolio[] = [];
  securities: InvestmentSecurity[] = [];
  loaded = false;

  creating = false;
  newName = '';
  newCurrency = 'EUR';
  createError = '';

  /** Notifica a las páginas de inversión (dashboard/operaciones) cuándo recargar su información. */
  private readonly portfolioIdSubject = new BehaviorSubject<number | null>(null);
  readonly portfolioId$ = this.portfolioIdSubject.asObservable();

  get portfolioId(): number | null {
    return this.portfolioIdSubject.value;
  }

  set portfolioId(id: number | null) {
    this.portfolioIdSubject.next(id);
  }

  get portfolio(): Portfolio | null {
    return this.portfolios.find(p => p.id === this.portfolioId) ?? null;
  }

  get baseCurrency(): string {
    return this.portfolio?.baseCurrency ?? 'EUR';
  }

  init(): void {
    this.api.getSecurities().subscribe(s => this.securities = s);
    this.api.getPortfolios().subscribe(list => {
      this.portfolios = list;
      if (!list.some(p => p.id === this.portfolioId)) {
        this.portfolioId = list[0]?.id ?? null;
      }
      this.loaded = true;
    });
  }

  startCreate(): void {
    this.creating = true;
    this.newName = '';
    this.newCurrency = 'EUR';
    this.createError = '';
  }

  cancelCreate(): void {
    this.creating = false;
    this.createError = '';
  }

  createPortfolio(): void {
    const name = this.newName.trim();
    const baseCurrency = this.newCurrency.trim().toUpperCase();
    if (!name || !baseCurrency) return;
    this.api.createPortfolio({ name, baseCurrency }).subscribe({
      next: created => {
        this.portfolios = [...this.portfolios, created];
        this.portfolioId = created.id ?? null;
        this.creating = false;
      },
      error: e => this.createError = e.error?.detail ?? e.error?.message ?? 'No se pudo crear la cartera.'
    });
  }
}
