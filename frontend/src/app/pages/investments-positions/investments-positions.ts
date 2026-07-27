import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnDestroy, OnInit, inject } from '@angular/core';
import { forkJoin, Subscription } from 'rxjs';
import { ApiService } from '../../api.service';
import { InvestmentContextService } from '../../investment-context.service';
import { InvestmentToolbar } from '../../components/investment-toolbar';
import { InvestmentPerformance, PositionPerformance, PositionView } from '../../models';

/** Paleta para el cuadrado de ticker de cada posición (alineada con el donut del Panel general). */
const ALLOCATION_COLORS = ['#2563EB', '#16A06B', '#E8A33D', '#8B5CF6', '#0891B2', '#E0453A', '#C2410C', '#4D7C0F'];

/**
 * Posiciones de la cartera seleccionada (§7): tabla con cantidades, coste,
 * precio, valor, P&L y rentabilidad (TWR/XIRR) por instrumento. La cartera
 * es estado compartido con Panel general/Operaciones vía
 * {@link InvestmentContextService}; se recarga sola cuando cambia.
 */
@Component({
  selector: 'app-investments-positions',
  imports: [CommonModule, InvestmentToolbar],
  templateUrl: './investments-positions.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './investments-positions.scss'
})
export class InvestmentsPositionsPage implements OnInit, OnDestroy {
  private api = inject(ApiService);
  readonly ctx = inject(InvestmentContextService);

  positions: PositionView[] = [];
  performance: InvestmentPerformance | null = null;

  private portfolioSub?: Subscription;

  ngOnInit(): void {
    this.portfolioSub = this.ctx.portfolioId$.subscribe(() => this.load());
    this.ctx.init();
  }

  ngOnDestroy(): void {
    this.portfolioSub?.unsubscribe();
  }

  get baseCurrency(): string {
    return this.ctx.baseCurrency;
  }

  /** Rentabilidad de una posición (para las columnas TWR/XIRR de la tabla). */
  perfOf(securityId: number): PositionPerformance | null {
    return this.performance?.positions.find(p => p.securityId === securityId) ?? null;
  }

  load(): void {
    const portfolioId = this.ctx.portfolioId;
    if (portfolioId == null) {
      this.positions = [];
      this.performance = null;
      return;
    }
    forkJoin({
      positions: this.api.getPositions(portfolioId),
      performance: this.api.getInvestmentPerformance(portfolioId)
    }).subscribe(r => {
      this.positions = r.positions;
      this.performance = r.performance;
    });
  }

  /** Etiqueta corta para el cuadrado de ticker de una posición. */
  tickerLabel(p: PositionView): string {
    return (p.ticker || p.name).slice(0, 4).toUpperCase();
  }

  /** Color del ticker de la posición i, alineado con la paleta del donut de asignación. */
  tickerColor(i: number): string {
    return ALLOCATION_COLORS[i % ALLOCATION_COLORS.length];
  }

  /** Fondo soft del ticker: el color de la posición con alfa suave. */
  tickerSoft(i: number): string {
    return `${this.tickerColor(i)}24`;
  }
}
