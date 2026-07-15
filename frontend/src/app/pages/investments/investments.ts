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
import {
  INVESTMENT_TYPE_LABELS, InvestmentIncome, InvestmentSecurity, InvestmentTransactionFilter,
  InvestmentTransactionType, InvestmentTransactionView, Portfolio, PortfolioSummary,
  PositionView, ValuationPoint
} from '../../models';

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

  // Pestañas de operaciones y dividendos (H2.4, §7).
  activeTab: 'operaciones' | 'dividendos' = 'operaciones';
  readonly typeLabels = INVESTMENT_TYPE_LABELS;
  readonly types = Object.keys(INVESTMENT_TYPE_LABELS) as InvestmentTransactionType[];
  securities: InvestmentSecurity[] = [];
  transactions: InvestmentTransactionView[] = [];
  filterType: InvestmentTransactionType | null = null;
  filterFrom = '';
  filterTo = '';
  filterSecurityId: number | null = null;

  income: InvestmentIncome | null = null;
  incomeYears: number[] = [];
  incomeYear: number | 'all' = new Date().getFullYear();

  @ViewChild(InvestmentTransactionDialog) txDialog?: InvestmentTransactionDialog;
  @ViewChild('allocationChart') allocationCanvas?: ElementRef<HTMLCanvasElement>;
  @ViewChild('evolutionChart') evolutionCanvas?: ElementRef<HTMLCanvasElement>;
  @ViewChild('pnlChart') pnlCanvas?: ElementRef<HTMLCanvasElement>;
  @ViewChild('dividendChart') dividendCanvas?: ElementRef<HTMLCanvasElement>;

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
    this.api.getSecurities().subscribe(s => this.securities = s);
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
      this.transactions = [];
      this.income = null;
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
    this.loadTransactions();
    this.loadIncome();
  }

  // ---- pestaña operaciones (listado filtrable, RF-2) ----

  setTab(tab: 'operaciones' | 'dividendos'): void {
    this.activeTab = tab;
    // El canvas de dividendos entra/sale del DOM con la pestaña.
    setTimeout(() => this.renderCharts());
  }

  loadTransactions(): void {
    if (this.portfolioId == null) return;
    const filter: InvestmentTransactionFilter = {};
    if (this.filterType) filter.type = this.filterType;
    if (this.filterFrom) filter.from = this.filterFrom;
    if (this.filterTo) filter.to = this.filterTo;
    if (this.filterSecurityId) filter.securityId = this.filterSecurityId;
    this.api.getInvestmentTransactions(this.portfolioId, filter)
      .subscribe(t => this.transactions = t);
  }

  editTransaction(tx: InvestmentTransactionView): void {
    this.txDialog?.edit(tx);
  }

  deleteTransaction(tx: InvestmentTransactionView): void {
    const label = this.typeLabels[tx.type].toLowerCase();
    if (!confirm(`¿Eliminar la operación de ${label} del ${tx.tradeDate}?`)) return;
    this.api.deleteInvestmentTransaction(tx.id).subscribe(() => this.load());
  }

  // ---- pestaña dividendos (RF-7, §7) ----

  loadIncome(): void {
    if (this.portfolioId == null) return;
    this.api.getInvestmentIncome(this.portfolioId).subscribe(income => {
      this.income = income;
      const years = new Set<number>();
      income.incomes.forEach(e => years.add(+e.month.slice(0, 4)));
      this.incomeYears = [...years].sort((a, b) => b - a);
      if (this.incomeYear !== 'all' && !this.incomeYears.includes(this.incomeYear)) {
        this.incomeYear = this.incomeYears[0] ?? new Date().getFullYear();
      }
      setTimeout(() => this.renderCharts());
    });
  }

  onIncomeYearChange(): void {
    setTimeout(() => this.renderCharts());
  }

  private inSelectedYear(month: string): boolean {
    return this.incomeYear === 'all' || +month.slice(0, 4) === this.incomeYear;
  }

  /** Cobros agregados por instrumento del año seleccionado (bruto/retenido/neto). */
  get incomeRows(): { name: string; gross: number; withheld: number; net: number }[] {
    if (!this.income) return [];
    const byName = new Map<string, { name: string; gross: number; withheld: number; net: number }>();
    for (const e of this.income.incomes) {
      if (!this.inSelectedYear(e.month)) continue;
      const name = e.name ?? 'Intereses';
      const row = byName.get(name) ?? { name, gross: 0, withheld: 0, net: 0 };
      row.gross += e.gross;
      row.withheld += e.withheld;
      row.net += e.net;
      byName.set(name, row);
    }
    return [...byName.values()].sort((a, b) => b.gross - a.gross);
  }

  get incomeTotals(): { gross: number; withheld: number; net: number } {
    return this.incomeRows.reduce(
      (acc, r) => ({ gross: acc.gross + r.gross, withheld: acc.withheld + r.withheld, net: acc.net + r.net }),
      { gross: 0, withheld: 0, net: 0 });
  }

  get feesTotal(): number {
    return (this.income?.fees ?? [])
      .filter(f => this.inSelectedYear(f.month))
      .reduce((sum, f) => sum + f.amount, 0);
  }

  get taxesTotal(): number {
    return (this.income?.taxes ?? [])
      .filter(t => this.inSelectedYear(t.month))
      .reduce((sum, t) => sum + t.amount, 0);
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
    this.renderDividends(text, grid);
  }

  /**
   * Barras mensuales apiladas por instrumento (§7): importes en bruto con el neto
   * en el tooltip; con «Todo» seleccionado, una barra apilada por año.
   */
  private renderDividends(text: string, grid: string): void {
    const canvas = this.dividendCanvas?.nativeElement;
    if (!canvas || !this.income) return;
    const yearsAsc = [...this.incomeYears].sort((a, b) => a - b);
    const labels = this.incomeYear === 'all'
      ? yearsAsc.map(String)
      : ['Ene', 'Feb', 'Mar', 'Abr', 'May', 'Jun', 'Jul', 'Ago', 'Sep', 'Oct', 'Nov', 'Dic'];

    type Series = { label: string; gross: number[]; net: number[] };
    const series = new Map<string, Series>();
    for (const e of this.income.incomes) {
      const index = this.incomeYear === 'all'
        ? yearsAsc.indexOf(+e.month.slice(0, 4))
        : (this.inSelectedYear(e.month) ? +e.month.slice(5, 7) - 1 : -1);
      if (index < 0) continue;
      const name = e.name ?? 'Intereses';
      const s = series.get(name)
        ?? { label: name, gross: labels.map(() => 0), net: labels.map(() => 0) };
      s.gross[index] += e.gross;
      s.net[index] += e.net;
      series.set(name, s);
    }

    const fmt = new Intl.NumberFormat('es-ES', { style: 'currency', currency: this.income.baseCurrency });
    const datasets = [...series.values()].map((s, i) => ({
      label: s.label,
      data: s.gross,
      backgroundColor: ALLOCATION_COLORS[i % ALLOCATION_COLORS.length],
      netData: s.net
    }));
    this.charts.push(new Chart(canvas, {
      type: 'bar',
      data: { labels, datasets },
      options: {
        responsive: true, maintainAspectRatio: false,
        plugins: {
          legend: { position: 'bottom', labels: { color: text } },
          tooltip: {
            callbacks: {
              label: ctx => {
                const net = (ctx.dataset as unknown as { netData: number[] }).netData[ctx.dataIndex];
                const gross = (ctx.parsed as { y: number }).y;
                return `${ctx.dataset.label}: ${fmt.format(gross)} bruto · ${fmt.format(net)} neto`;
              }
            }
          }
        },
        scales: {
          x: { stacked: true, ticks: { color: text }, grid: { color: grid } },
          y: { stacked: true, ticks: { color: text }, grid: { color: grid } }
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
