import { CommonModule } from '@angular/common';
import {
  AfterViewInit, Component, ElementRef, OnDestroy, OnInit, ViewChild, effect, inject
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Chart, registerables } from 'chart.js';
import { forkJoin } from 'rxjs';
import { ApiService } from '../../api.service';
import { ThemeService } from '../../theme.service';
import { FlexImportDialog } from '../../components/flex-import-dialog';
import { InvestmentTransactionDialog } from '../../components/investment-transaction-dialog';
import { Portfolio, PortfolioSummary, PositionView, ValuationPoint } from '../../models';

Chart.register(...registerables);

/** Paleta para las porciones del donut de asignación (efectivo aparte, en gris). */
const ALLOCATION_COLORS = ['#1d6b48', '#2563eb', '#96762a', '#7c3aed', '#0891b2', '#9c2a23', '#c2410c', '#4d7c0f'];
const CASH_COLOR = '#94a3b8';

@Component({
  selector: 'app-investments',
  imports: [CommonModule, FormsModule, FlexImportDialog, InvestmentTransactionDialog],
  templateUrl: './investments.html',
  styleUrl: './investments.scss'
})
export class InvestmentsPage implements OnInit, AfterViewInit, OnDestroy {
  private api = inject(ApiService);
  private theme = inject(ThemeService);

  readonly currentYear = new Date().getFullYear();

  portfolios: Portfolio[] = [];
  portfolioId: number | null = null;
  summary: PortfolioSummary | null = null;
  positions: PositionView[] = [];
  history: ValuationPoint[] = [];
  loaded = false;

  // Alta de cartera (inline en la toolbar).
  creating = false;
  newName = '';
  newCurrency = 'EUR';
  createError = '';

  @ViewChild('allocationChart') allocationCanvas?: ElementRef<HTMLCanvasElement>;
  @ViewChild('evolutionChart') evolutionCanvas?: ElementRef<HTMLCanvasElement>;
  @ViewChild('pnlChart') pnlCanvas?: ElementRef<HTMLCanvasElement>;

  private charts: Chart[] = [];
  private viewReady = false;

  constructor() {
    // Redibuja los gráficos con los colores del tema al cambiarlo.
    effect(() => {
      this.theme.theme();
      if (this.viewReady) this.renderCharts();
    });
  }

  ngOnInit(): void {
    this.api.getPortfolios().subscribe(list => {
      this.portfolios = list;
      this.portfolioId = list[0]?.id ?? null;
      this.loaded = true;
      this.load();
    });
  }

  ngAfterViewInit(): void {
    this.viewReady = true;
    this.renderCharts();
  }

  ngOnDestroy(): void {
    this.charts.forEach(c => c.destroy());
  }

  get portfolio(): Portfolio | null {
    return this.portfolios.find(p => p.id === this.portfolioId) ?? null;
  }

  get baseCurrency(): string {
    return this.summary?.baseCurrency ?? this.portfolio?.baseCurrency ?? 'EUR';
  }

  get cashEntries(): { currency: string; amount: number }[] {
    if (!this.summary) return [];
    return Object.entries(this.summary.cashByCurrency)
      .map(([currency, amount]) => ({ currency, amount }));
  }

  onPortfolioChange(): void {
    this.load();
  }

  load(): void {
    if (this.portfolioId == null) {
      this.summary = null;
      this.positions = [];
      this.history = [];
      this.renderCharts();
      return;
    }
    forkJoin({
      summary: this.api.getPortfolioSummary(this.portfolioId),
      positions: this.api.getPositions(this.portfolioId),
      history: this.api.getValuationHistory(this.portfolioId)
    }).subscribe(r => {
      this.summary = r.summary;
      this.positions = r.positions;
      this.history = r.history;
      // Los canvas viven dentro de @if (summary): esperar a que el DOM se actualice.
      setTimeout(() => this.renderCharts());
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
        this.load();
      },
      error: e => this.createError = e.error?.detail ?? e.error?.message ?? 'No se pudo crear la cartera.'
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
            borderColor: '#2563eb', backgroundColor: 'rgba(37,99,235,.12)',
            fill: true, tension: 0, spanGaps: true
          },
          {
            label: 'Aportado', data: this.history.map(p => p.contributed),
            borderColor: '#96762a', backgroundColor: '#96762a',
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
          backgroundColor: sorted.map(p => (p.latentPnl >= 0 ? '#1d6b48' : '#9c2a23'))
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
