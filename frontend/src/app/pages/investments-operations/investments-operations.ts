import { CommonModule } from '@angular/common';
import {
  AfterViewInit, Component, ElementRef, OnDestroy, OnInit, ViewChild, inject, ChangeDetectionStrategy
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Chart, registerables } from 'chart.js';
import { Subscription } from 'rxjs';
import { ApiService } from '../../api.service';
import { ThemeService } from '../../theme.service';
import { InvestmentContextService } from '../../investment-context.service';
import { InvestmentToolbar } from '../../components/investment-toolbar';
import { Pagination } from '../../components/pagination';
import {
  INVESTMENT_TYPE_LABELS, InvestmentIncome, InvestmentTransactionFilter, InvestmentTransactionType,
  InvestmentTransactionView
} from '../../models';

Chart.register(...registerables);
Chart.defaults.font.family = "'JetBrains Mono', ui-monospace, monospace";

const ALLOCATION_COLORS = ['#2563EB', '#16A06B', '#E8A33D', '#8B5CF6', '#0891B2', '#E0453A', '#C2410C', '#4D7C0F'];

/**
 * Operaciones de inversión (§7): pestañas Operaciones (listado filtrable +
 * alta/edición/borrado manual, paginado) y Dividendos (rentas por
 * instrumento/año). La cartera es estado compartido con el Panel general vía
 * {@link InvestmentContextService}; se recarga sola cuando cambia.
 */
@Component({
  selector: 'app-investments-operations',
  imports: [CommonModule, FormsModule, InvestmentToolbar, Pagination],
  templateUrl: './investments-operations.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './investments-operations.scss'
})
export class InvestmentsOperationsPage implements OnInit, AfterViewInit, OnDestroy {
  private api = inject(ApiService);
  private theme = inject(ThemeService);
  readonly ctx = inject(InvestmentContextService);

  activeTab: 'operaciones' | 'dividendos' = 'operaciones';
  readonly typeLabels = INVESTMENT_TYPE_LABELS;
  readonly types = Object.keys(INVESTMENT_TYPE_LABELS) as InvestmentTransactionType[];
  transactions: InvestmentTransactionView[] = [];
  filterType: InvestmentTransactionType | null = null;
  filterFrom = '';
  filterTo = '';
  filterSecurityId: number | null = null;
  txPage = 0;
  txSize = 25;
  txTotalElements = 0;

  income: InvestmentIncome | null = null;
  incomeYears: number[] = [];
  incomeYear: number | 'all' = new Date().getFullYear();

  @ViewChild(InvestmentToolbar) toolbar?: InvestmentToolbar;
  @ViewChild('dividendChart') dividendCanvas?: ElementRef<HTMLCanvasElement>;

  private charts: Chart[] = [];
  private viewReady = false;
  private renderScheduled = false;
  private portfolioSub?: Subscription;

  ngOnInit(): void {
    this.portfolioSub = this.ctx.portfolioId$.subscribe(() => {
      this.loadTransactions();
      this.loadIncome();
    });
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

  get baseCurrency(): string {
    return this.ctx.baseCurrency;
  }

  // ---- pestaña operaciones (listado filtrable, RF-2) ----

  setTab(tab: 'operaciones' | 'dividendos'): void {
    this.activeTab = tab;
    // El canvas de dividendos entra/sale del DOM con la pestaña.
    this.scheduleRenderCharts();
  }

  loadTransactions(): void {
    const portfolioId = this.ctx.portfolioId;
    if (portfolioId == null) return;
    const filter: InvestmentTransactionFilter = {};
    if (this.filterType) filter.type = this.filterType;
    if (this.filterFrom) filter.from = this.filterFrom;
    if (this.filterTo) filter.to = this.filterTo;
    if (this.filterSecurityId) filter.securityId = this.filterSecurityId;
    this.api.getInvestmentTransactions(portfolioId, filter, this.txPage, this.txSize)
      .subscribe(p => {
        this.transactions = p.content;
        this.txTotalElements = p.totalElements;
      });
  }

  /** A filter change makes the current page number meaningless: back to the first page. */
  onFiltersChange(): void {
    this.txPage = 0;
    this.loadTransactions();
  }

  onTxPageChange(page: number): void {
    this.txPage = page;
    this.loadTransactions();
  }

  /**
   * A size change makes the current page number meaningless too: reset it
   * here, in the same handler, so only one reload fires with both values
   * already updated — never two independent reloads (one of them still
   * reading the old size) racing each other.
   */
  onTxSizeChange(size: number): void {
    this.txPage = 0;
    this.txSize = size;
    this.loadTransactions();
  }

  editTransaction(tx: InvestmentTransactionView): void {
    this.toolbar?.edit(tx);
  }

  deleteTransaction(tx: InvestmentTransactionView): void {
    const label = this.typeLabels[tx.type].toLowerCase();
    if (!confirm(`¿Eliminar la operación de ${label} del ${tx.tradeDate}?`)) return;
    this.api.deleteInvestmentTransaction(tx.id).subscribe(() => this.loadTransactions());
  }

  // ---- pestaña dividendos (RF-7, §7) ----

  loadIncome(): void {
    const portfolioId = this.ctx.portfolioId;
    if (portfolioId == null) {
      this.income = null;
      this.renderCharts();
      return;
    }
    this.api.getInvestmentIncome(portfolioId).subscribe(income => {
      this.income = income;
      const years = new Set<number>();
      income.incomes.forEach(e => years.add(+e.month.slice(0, 4)));
      this.incomeYears = [...years].sort((a, b) => b - a);
      if (this.incomeYear !== 'all' && !this.incomeYears.includes(this.incomeYear)) {
        this.incomeYear = this.incomeYears[0] ?? new Date().getFullYear();
      }
      this.scheduleRenderCharts();
    });
  }

  onIncomeYearChange(): void {
    this.scheduleRenderCharts();
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

  /** Ver comentario homónimo en investments-dashboard.ts: mismo problema de layout de canvas. */
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
    if (!(this.activeTab === 'dividendos' && this.income && this.incomeRows.length > 0)) return true;
    return !!this.dividendCanvas
      && (this.dividendCanvas.nativeElement.parentElement?.getBoundingClientRect().width ?? 0) > 0;
  }

  private renderCharts(): void {
    if (!this.viewReady) return;
    this.charts.forEach(c => c.destroy());
    this.charts = [];

    const text = this.theme.chartText();
    const grid = this.theme.chartGrid();
    Chart.defaults.color = text;

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
}
