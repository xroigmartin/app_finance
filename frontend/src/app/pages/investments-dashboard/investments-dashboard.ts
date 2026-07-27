import { CommonModule } from '@angular/common';
import {
  AfterViewInit, Component, ElementRef, OnDestroy, OnInit, ViewChild, effect, inject,
  ChangeDetectionStrategy
} from '@angular/core';
import { Chart, registerables } from 'chart.js';
import { forkJoin, Subscription } from 'rxjs';
import { ApiService } from '../../api.service';
import { ThemeService } from '../../theme.service';
import { InvestmentContextService } from '../../investment-context.service';
import { InvestmentToolbar } from '../../components/investment-toolbar';
import { InvestmentPerformance, PortfolioSummary, PositionView, ValuationPoint } from '../../models';

Chart.register(...registerables);
Chart.defaults.font.family = "'JetBrains Mono', ui-monospace, monospace";

/** Paleta para las porciones del donut de asignación (efectivo aparte, en gris). */
const ALLOCATION_COLORS = ['#2563EB', '#16A06B', '#E8A33D', '#8B5CF6', '#0891B2', '#E0453A', '#C2410C', '#4D7C0F'];
const CASH_COLOR = '#94a3b8';

/**
 * Panel general de inversión (§7): KPIs y gráficos (asignación, evolución,
 * P&L, rentabilidad) de la cartera seleccionada. La tabla de posiciones vive
 * en `pages/investments-positions/`. La cartera es estado compartido con
 * Operaciones y Posiciones vía {@link InvestmentContextService}; se recarga
 * sola cuando cambia (toolbar, alta de cartera...).
 */
@Component({
  selector: 'app-investments-dashboard',
  imports: [CommonModule, InvestmentToolbar],
  templateUrl: './investments-dashboard.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './investments-dashboard.scss'
})
export class InvestmentsDashboardPage implements OnInit, AfterViewInit, OnDestroy {
  private api = inject(ApiService);
  private theme = inject(ThemeService);
  readonly ctx = inject(InvestmentContextService);

  readonly currentYear = new Date().getFullYear();

  summary: PortfolioSummary | null = null;
  positions: PositionView[] = [];
  history: ValuationPoint[] = [];
  performance: InvestmentPerformance | null = null;

  @ViewChild('allocationChart') allocationCanvas?: ElementRef<HTMLCanvasElement>;
  @ViewChild('evolutionChart') evolutionCanvas?: ElementRef<HTMLCanvasElement>;
  @ViewChild('pnlChart') pnlCanvas?: ElementRef<HTMLCanvasElement>;
  @ViewChild('performanceChart') performanceCanvas?: ElementRef<HTMLCanvasElement>;

  private charts: Chart[] = [];
  private viewReady = false;
  private renderScheduled = false;
  private portfolioSub?: Subscription;

  constructor() {
    // Redibuja los gráficos con los colores del tema al cambiarlo.
    effect(() => {
      this.theme.theme();
      if (this.viewReady) this.renderCharts();
    });
  }

  ngOnInit(): void {
    this.portfolioSub = this.ctx.portfolioId$.subscribe(() => this.load());
    this.ctx.init();
  }

  ngAfterViewInit(): void {
    this.viewReady = true;
    this.renderCharts();
  }

  ngOnDestroy(): void {
    this.portfolioSub?.unsubscribe();
    this.charts.forEach(c => c.destroy());
  }

  /** Ver comentario homónimo en la antigua página de inversión (H2.4, §7): mismo problema de layout de canvas. */
  private scheduleRenderCharts(attempt = 0): void {
    if (this.renderScheduled) return;
    this.renderScheduled = true;
    requestAnimationFrame(() => {
      this.renderScheduled = false;
      if (!this.chartsLayoutReady() && attempt < 30) {
        this.scheduleRenderCharts(attempt + 1);
        return;
      }
      this.renderCharts();
    });
  }

  private chartsLayoutReady(): boolean {
    if (!this.summary) return true;
    const expected = [this.allocationCanvas, this.evolutionCanvas, this.pnlCanvas, this.performanceCanvas];
    return expected.every(ref =>
      !!ref && (ref.nativeElement.parentElement?.getBoundingClientRect().width ?? 0) > 0);
  }

  get baseCurrency(): string {
    return this.summary?.baseCurrency ?? this.ctx.baseCurrency;
  }

  get cashEntries(): { currency: string; amount: number }[] {
    if (!this.summary) return [];
    return Object.entries(this.summary.cashByCurrency)
      .map(([currency, amount]) => ({ currency, amount }));
  }

  load(): void {
    const portfolioId = this.ctx.portfolioId;
    if (portfolioId == null) {
      this.summary = null;
      this.positions = [];
      this.history = [];
      this.performance = null;
      this.renderCharts();
      return;
    }
    forkJoin({
      summary: this.api.getPortfolioSummary(portfolioId),
      positions: this.api.getPositions(portfolioId),
      history: this.api.getValuationHistory(portfolioId),
      performance: this.api.getInvestmentPerformance(portfolioId)
    }).subscribe(r => {
      this.summary = r.summary;
      this.positions = r.positions;
      this.history = r.history;
      this.performance = r.performance;
      // Los canvas viven dentro de @if (summary): esperar a que el DOM se actualice.
      this.scheduleRenderCharts();
    });
  }

  private renderCharts(): void {
    if (!this.viewReady) return;
    this.charts.forEach(c => c.destroy());
    this.charts = [];

    const text = this.theme.chartText();
    const grid = this.theme.chartGrid();
    Chart.defaults.color = text;

    this.renderAllocation(text);
    this.renderEvolution(text, grid);
    this.renderPnl(text, grid);
    this.renderPerformance(text, grid);
  }

  /** Barras horizontales de rentabilidad por posición: TWR acumulada y XIRR anual, en % (§7). */
  private renderPerformance(text: string, grid: string): void {
    const canvas = this.performanceCanvas?.nativeElement;
    if (!canvas || !this.performance) return;
    const sorted = [...this.performance.positions]
      .sort((a, b) => (b.xirrPercent ?? -Infinity) - (a.xirrPercent ?? -Infinity));
    this.charts.push(new Chart(canvas, {
      type: 'bar',
      data: {
        labels: sorted.map(p => p.name),
        datasets: [
          { label: 'XIRR anual', data: sorted.map(p => p.xirrPercent), backgroundColor: this.theme.chartAccent() },
          { label: 'TWR acumulada', data: sorted.map(p => p.twrPercent), backgroundColor: this.theme.chartWarn() }
        ]
      },
      options: {
        indexAxis: 'y',
        responsive: true, maintainAspectRatio: false,
        plugins: {
          legend: { position: 'bottom', labels: { color: text } },
          tooltip: {
            callbacks: {
              label: ctx => `${ctx.dataset.label}: ${(ctx.parsed as { x: number }).x?.toFixed(2) ?? '—'} %`
            }
          }
        },
        scales: {
          x: { ticks: { color: text, callback: value => `${value} %` }, grid: { color: grid } },
          y: { ticks: { color: text }, grid: { display: false } }
        }
      }
    }));
  }

  /** Donut de asignación: posiciones abiertas + el efectivo como una porción más (§7). */
  private renderAllocation(text: string): void {
    const canvas = this.allocationCanvas?.nativeElement;
    if (!canvas || !this.summary) return;
    const open = this.positions.filter(p => p.marketValue > 0);
    const positionsValue = open.reduce((sum, p) => sum + p.marketValue, 0);
    const cash = Math.max(this.summary.totalValue - positionsValue, 0);
    const labels = [...open.map(p => p.name), 'Efectivo'];
    const values = [...open.map(p => p.marketValue), cash];
    const colors = [...open.map((_, i) => ALLOCATION_COLORS[i % ALLOCATION_COLORS.length]), CASH_COLOR];
    this.charts.push(new Chart(canvas, {
      type: 'doughnut',
      data: { labels, datasets: [{ data: values, backgroundColor: colors, borderWidth: 0 }] },
      options: {
        responsive: true, maintainAspectRatio: false,
        plugins: { legend: { position: 'bottom', labels: { color: text } } }
      }
    }));
  }

  /** Evolución valor vs aportado (§7): aportado escalonado exacto, valor solo donde hay cotización. */
  private renderEvolution(text: string, grid: string): void {
    const canvas = this.evolutionCanvas?.nativeElement;
    if (!canvas) return;
    this.charts.push(new Chart(canvas, {
      type: 'line',
      data: {
        labels: this.history.map(p => p.date),
        datasets: [
          {
            label: 'Valor', data: this.history.map(p => p.value),
            borderColor: this.theme.chartAccent(), backgroundColor: this.theme.chartAccentSoft(),
            fill: true, tension: 0, spanGaps: true
          },
          {
            label: 'Aportado', data: this.history.map(p => p.contributed),
            borderColor: this.theme.chartText(), backgroundColor: this.theme.chartText(),
            stepped: true, pointRadius: 0
          }
        ]
      },
      options: {
        responsive: true, maintainAspectRatio: false,
        plugins: { legend: { position: 'bottom', labels: { color: text } } },
        scales: {
          x: { ticks: { color: text }, grid: { color: grid } },
          y: { ticks: { color: text }, grid: { color: grid } }
        }
      }
    }));
  }

  /** Barras horizontales divergentes de P&L latente por posición, de mejor a peor (§7). */
  private renderPnl(text: string, grid: string): void {
    const canvas = this.pnlCanvas?.nativeElement;
    if (!canvas) return;
    const sorted = [...this.positions].sort((a, b) => b.latentPnl - a.latentPnl);
    this.charts.push(new Chart(canvas, {
      type: 'bar',
      data: {
        labels: sorted.map(p => p.name),
        datasets: [{
          label: 'P&L latente',
          data: sorted.map(p => p.latentPnl),
          backgroundColor: sorted.map(p => (p.latentPnl >= 0 ? this.theme.chartPos() : this.theme.chartNeg()))
        }]
      },
      options: {
        indexAxis: 'y',
        responsive: true, maintainAspectRatio: false,
        plugins: { legend: { display: false } },
        scales: {
          x: { ticks: { color: text }, grid: { color: grid } },
          y: { ticks: { color: text }, grid: { display: false } }
        }
      }
    }));
  }
}
