import { CommonModule } from '@angular/common';
import {
  AfterViewInit, Component, ElementRef, OnDestroy, OnInit, ViewChild, effect, inject,
  ChangeDetectionStrategy
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Chart, registerables } from 'chart.js';
import { forkJoin } from 'rxjs';
import { ApiService } from '../../api.service';
import { ThemeService } from '../../theme.service';
import {
  Account, AccountComparison, BalancePoint, BudgetStatus, CategoryAmount,
  InvestmentsSummary, MonthlyPoint, Summary, Transaction
} from '../../models';

Chart.register(...registerables);
Chart.defaults.font.family = "'JetBrains Mono', ui-monospace, monospace";

/** Palette for per-account comparison series (accent first, then distinguishable mid-tones). */
const ACCOUNT_COLORS = ['#2563EB', '#16A06B', '#E8A33D', '#8B5CF6', '#0891B2', '#E0453A', '#C2410C'];

@Component({
  selector: 'app-dashboard',
  imports: [CommonModule, FormsModule],
  templateUrl: './dashboard.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './dashboard.scss'
})
export class DashboardPage implements OnInit, AfterViewInit, OnDestroy {
  private api = inject(ApiService);
  private theme = inject(ThemeService);

  summary: Summary | null = null;
  recent: Transaction[] = [];
  budgets: BudgetStatus[] = [];
  accounts: Account[] = [];

  // Tarjeta de patrimonio invertido (RF-10 del PRD Inversiones): informativa e
  // independiente de los filtros; se oculta sin carteras y degrada a "—" si la
  // API de inversiones falla, sin tumbar el resto del dashboard.
  investments: InvestmentsSummary | null = null;
  investmentsError = false;

  year = new Date().getFullYear();
  month = new Date().getMonth() + 1;
  accountId: number | null = null;
  private readonly monthNames = [
    'Enero', 'Febrero', 'Marzo', 'Abril', 'Mayo', 'Junio',
    'Julio', 'Agosto', 'Septiembre', 'Octubre', 'Noviembre', 'Diciembre'
  ];

  @ViewChild('monthlyChart') monthlyCanvas!: ElementRef<HTMLCanvasElement>;
  @ViewChild('categoryChart') categoryCanvas!: ElementRef<HTMLCanvasElement>;
  @ViewChild('incomeCategoryChart') incomeCategoryCanvas!: ElementRef<HTMLCanvasElement>;
  @ViewChild('savingsChart') savingsCanvas!: ElementRef<HTMLCanvasElement>;
  @ViewChild('balanceChart') balanceCanvas!: ElementRef<HTMLCanvasElement>;
  @ViewChild('incomeCompareChart') incomeCompareCanvas!: ElementRef<HTMLCanvasElement>;
  @ViewChild('expenseCompareChart') expenseCompareCanvas!: ElementRef<HTMLCanvasElement>;

  private charts: Chart[] = [];
  private viewReady = false;
  private dataLoaded = false;

  // Latest fetched data, kept so charts can be redrawn on a theme change.
  private monthlyData: MonthlyPoint[] = [];
  private expenseCatData: CategoryAmount[] = [];
  private incomeCatData: CategoryAmount[] = [];
  private balanceData: BalancePoint[] = [];
  private comparison: AccountComparison | null = null;

  constructor() {
    // Redraw charts (with theme-aware colours) whenever the theme switches.
    effect(() => {
      this.theme.theme();
      if (this.viewReady) this.renderCharts();
    });
  }

  ngOnInit(): void {
    this.api.getAccounts().subscribe(a => this.accounts = a);
    this.api.getRecentTransactions().subscribe(t => this.recent = t);
    this.api.getInvestmentsSummary().subscribe({
      next: s => this.investments = s,
      error: () => this.investmentsError = true
    });
    this.load();
  }

  get accountLabel(): string {
    return this.accounts.find(a => a.id === this.accountId)?.name ?? 'Todas las cuentas';
  }

  onAccountChange(): void {
    this.load();
  }

  get monthLabel(): string {
    return `${this.monthNames[this.month - 1]} ${this.year}`;
  }

  get isCurrentMonth(): boolean {
    const now = new Date();
    return this.year === now.getFullYear() && this.month === now.getMonth() + 1;
  }

  prevMonth(): void {
    this.month--;
    if (this.month === 0) {
      this.month = 12;
      this.year--;
    }
    this.load();
  }

  nextMonth(): void {
    this.month++;
    if (this.month === 13) {
      this.month = 1;
      this.year++;
    }
    this.load();
  }

  goToCurrentMonth(): void {
    this.year = new Date().getFullYear();
    this.month = new Date().getMonth() + 1;
    this.load();
  }

  private load(): void {
    const account = this.accountId ?? undefined;
    this.api.getSummary(this.year, this.month, account).subscribe(s => this.summary = s);
    this.api.getBudgetStatus(this.year, this.month, account).subscribe(b => this.budgets = b);
    this.fetchChartData();
  }

  percent(s: BudgetStatus): number {
    return s.budget > 0 ? Math.min(Math.round((s.spent / s.budget) * 100), 100) : 0;
  }

  overBudget(s: BudgetStatus): boolean {
    return s.spent > s.budget;
  }

  /** Fondo "soft" para el avatar de un movimiento: el color de su categoría con alfa suave. */
  soft(color: string): string {
    return /^#[0-9a-fA-F]{6}$/.test(color) ? `${color}24` : color;
  }

  ngAfterViewInit(): void {
    this.viewReady = true;
    this.renderCharts();
  }

  ngOnDestroy(): void {
    this.charts.forEach(c => c.destroy());
  }

  private fetchChartData(): void {
    const account = this.accountId ?? undefined;
    forkJoin({
      monthly: this.api.getMonthly(12, this.year, this.month, account),
      expenseCat: this.api.getExpensesByCategory(this.year, this.month, account),
      incomeCat: this.api.getIncomeByCategory(this.year, this.month, account),
      balance: this.api.getMonthlyBalance(12, this.year, this.month, account),
      comparison: this.api.getAccountComparison(12, this.year, this.month)
    }).subscribe(r => {
      this.monthlyData = r.monthly;
      this.expenseCatData = r.expenseCat;
      this.incomeCatData = r.incomeCat;
      this.balanceData = r.balance;
      this.comparison = r.comparison;
      this.dataLoaded = true;
      this.renderCharts();
    });
  }

  private renderCharts(): void {
    // ngAfterViewInit se dispara antes de que resuelvan las peticiones HTTP (asíncronas);
    // sin esta guarda se crean los gráficos con datasets vacíos y se destruyen/recrean
    // en cuanto llegan los datos reales, lo que a veces deja el canvas en blanco.
    if (!this.viewReady || !this.dataLoaded) return;
    this.charts.forEach(c => c.destroy());
    this.charts = [];

    const text = this.theme.chartText();
    const grid = this.theme.chartGrid();
    Chart.defaults.color = text;

    // Fresh option objects per chart: Chart.js mutates these internally.
    const axisX = () => ({ ticks: { color: text }, grid: { color: grid } });
    const axisY = () => ({ beginAtZero: true, ticks: { color: text }, grid: { color: grid } });
    const legendBottom = () => ({ legend: { position: 'bottom' as const, labels: { color: text } } });

    // Evolución 12 meses: ingresos vs gastos
    this.charts.push(new Chart(this.monthlyCanvas.nativeElement, {
      type: 'bar',
      data: {
        labels: this.monthlyData.map(p => p.month),
        datasets: [
          { label: 'Ingresos', data: this.monthlyData.map(p => p.income), backgroundColor: this.theme.chartPos() },
          { label: 'Gastos', data: this.monthlyData.map(p => p.expense), backgroundColor: this.theme.chartNeg() }
        ]
      },
      options: {
        responsive: true, maintainAspectRatio: false,
        plugins: legendBottom(), scales: { x: axisX(), y: axisY() }
      }
    }));

    // Gastos por categoría
    this.charts.push(this.doughnut(this.categoryCanvas.nativeElement, this.expenseCatData, text));
    // Ingresos por categoría
    this.charts.push(this.doughnut(this.incomeCategoryCanvas.nativeElement, this.incomeCatData, text));

    // Ahorros acumulados: barra mensual + línea acumulada
    const savings = this.monthlyData.map(p => p.income - p.expense);
    let running = 0;
    const cumulative = savings.map(s => (running += s));
    this.charts.push(new Chart(this.savingsCanvas.nativeElement, {
      data: {
        labels: this.monthlyData.map(p => p.month),
        datasets: [
          {
            type: 'bar', label: 'Ahorro mensual', data: savings,
            backgroundColor: savings.map(s => (s >= 0 ? this.theme.chartPos() : this.theme.chartNeg())), yAxisID: 'y'
          },
          {
            type: 'line', label: 'Acumulado', data: cumulative,
            borderColor: this.theme.chartAccent(), backgroundColor: this.theme.chartAccent(),
            tension: .3, yAxisID: 'y1'
          }
        ]
      },
      options: {
        responsive: true, maintainAspectRatio: false, plugins: legendBottom(),
        scales: {
          x: axisX(),
          y: { ticks: { color: text }, grid: { color: grid } },
          y1: { position: 'right', ticks: { color: text }, grid: { drawOnChartArea: false } }
        }
      }
    }));

    // Evolución del saldo (patrimonio a fin de cada mes)
    this.charts.push(new Chart(this.balanceCanvas.nativeElement, {
      type: 'line',
      data: {
        labels: this.balanceData.map(p => p.month),
        datasets: [{
          label: 'Saldo', data: this.balanceData.map(p => p.balance),
          borderColor: this.theme.chartAccent(), backgroundColor: this.theme.chartAccentSoft(),
          fill: true, tension: .3
        }]
      },
      options: {
        responsive: true, maintainAspectRatio: false, plugins: legendBottom(),
        scales: { x: axisX(), y: { ticks: { color: text }, grid: { color: grid } } }
      }
    }));

    // Comparativas por cuenta
    if (this.comparison) {
      this.charts.push(this.compareChart(
        this.incomeCompareCanvas.nativeElement, this.comparison, 'income', text, grid));
      this.charts.push(this.compareChart(
        this.expenseCompareCanvas.nativeElement, this.comparison, 'expense', text, grid));
    }
  }

  private doughnut(canvas: HTMLCanvasElement, items: CategoryAmount[], text: string): Chart {
    return new Chart(canvas, {
      type: 'doughnut',
      data: {
        labels: items.map(i => i.category),
        datasets: [{ data: items.map(i => i.amount), backgroundColor: items.map(i => i.color), borderWidth: 0 }]
      },
      options: {
        responsive: true, maintainAspectRatio: false,
        plugins: { legend: { position: 'bottom', labels: { color: text } } }
      }
    });
  }

  private compareChart(canvas: HTMLCanvasElement, c: AccountComparison,
                       field: 'income' | 'expense', text: string, grid: string): Chart {
    return new Chart(canvas, {
      type: 'line',
      data: {
        labels: c.months,
        datasets: c.accounts.map((a, i) => {
          const color = ACCOUNT_COLORS[i % ACCOUNT_COLORS.length];
          return { label: a.name, data: a[field], borderColor: color, backgroundColor: color, tension: .3 };
        })
      },
      options: {
        responsive: true, maintainAspectRatio: false,
        plugins: { legend: { position: 'bottom', labels: { color: text } } },
        scales: {
          x: { ticks: { color: text }, grid: { color: grid } },
          y: { beginAtZero: true, ticks: { color: text }, grid: { color: grid } }
        }
      }
    });
  }
}
