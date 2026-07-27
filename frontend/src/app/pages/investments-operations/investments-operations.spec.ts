import { ElementRef } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { ApiService } from '../../api.service';
import { InvestmentContextService } from '../../investment-context.service';
import { InvestmentToolbar } from '../../components/investment-toolbar';
import {
  ImportRecordView, InvestmentIncome, InvestmentSecurity, InvestmentTransactionView, PageResponse, Portfolio
} from '../../models';
import { InvestmentsOperationsPage } from './investments-operations';

vi.mock('chart.js', () => {
  class MockChart {
    static register = vi.fn();
    static defaults: Record<string, unknown> = { font: {} };
    destroy = vi.fn();
    canvas: unknown;
    config: unknown;
    constructor(canvas: unknown, config: unknown) {
      this.canvas = canvas;
      this.config = config;
    }
  }
  return { Chart: MockChart, registerables: [] };
});

describe('InvestmentsOperationsPage', () => {
  afterEach(() => vi.restoreAllMocks());

  let api: {
    getSecurities: ReturnType<typeof vi.fn>;
    getPortfolios: ReturnType<typeof vi.fn>;
    getInvestmentTransactions: ReturnType<typeof vi.fn>;
    getInvestmentIncome: ReturnType<typeof vi.fn>;
    deleteInvestmentTransaction: ReturnType<typeof vi.fn>;
    getImportHistory: ReturnType<typeof vi.fn>;
  };

  const portfolio: Portfolio = { id: 1, name: 'Cartera E2E', baseCurrency: 'EUR' };
  const security: InvestmentSecurity = {
    id: 9, isin: 'US1', currency: 'EUR', name: 'Empresa', ticker: 'EMP', type: 'EQUITY', exchange: 'XETRA', figi: null,
  };
  const transactions: InvestmentTransactionView[] = [];
  const transactionsPage: PageResponse<InvestmentTransactionView> = {
    content: transactions, page: 0, size: 25, totalElements: 0, totalPages: 0,
  };
  const income: InvestmentIncome = {
    portfolioId: 1, baseCurrency: 'EUR',
    incomes: [
      { securityId: 9, name: 'Empresa', month: '2026-03', gross: 10, withheld: 1.5, net: 8.5 },
      { securityId: 9, name: 'Empresa', month: '2025-03', gross: 5, withheld: 0.75, net: 4.25 },
    ],
    fees: [{ month: '2026-03', amount: 2 }, { month: '2025-03', amount: 1 }],
    taxes: [{ month: '2026-03', amount: 0.5 }, { month: '2025-03', amount: 0.25 }],
  };

  const historyPage: PageResponse<ImportRecordView> = {
    content: [], page: 0, size: 25, totalElements: 0, totalPages: 0,
  };

  function create(overrides: Partial<typeof api> = {}, portfolios: Portfolio[] = [portfolio]):
    { page: InvestmentsOperationsPage; ctx: InvestmentContextService } {
    api = {
      getSecurities: vi.fn().mockReturnValue(of([security])),
      getPortfolios: vi.fn().mockReturnValue(of(portfolios)),
      getInvestmentTransactions: vi.fn().mockReturnValue(of(transactionsPage)),
      getInvestmentIncome: vi.fn().mockReturnValue(of(income)),
      deleteInvestmentTransaction: vi.fn().mockReturnValue(of(undefined)),
      getImportHistory: vi.fn().mockReturnValue(of(historyPage)),
      ...overrides,
    };
    TestBed.configureTestingModule({ providers: [{ provide: ApiService, useValue: api }] });
    const ctx = TestBed.inject(InvestmentContextService);
    const page = TestBed.createComponent(InvestmentsOperationsPage).componentInstance;
    return { page, ctx };
  }

  function fakeCanvas(): ElementRef<HTMLCanvasElement> {
    return { nativeElement: {} } as ElementRef<HTMLCanvasElement>;
  }

  it('ngOnInit inicializa el contexto compartido y carga operaciones + rentas', () => {
    const { page, ctx } = create();
    page.ngOnInit();
    expect(ctx.portfolioId).toBe(1);
    expect(page.income).toEqual(income);
  });

  it('reacciona a un cambio de cartera en el contexto compartido', () => {
    const { page, ctx } = create();
    page.ngOnInit();
    api.getInvestmentTransactions.mockClear();
    ctx.portfolios = [portfolio, { id: 2, name: 'Otra', baseCurrency: 'USD' }];
    ctx.portfolioId = 2;
    expect(api.getInvestmentTransactions).toHaveBeenCalledWith(2, {}, 0, 25);
  });

  it('ngOnDestroy cierra la suscripción al contexto', () => {
    const { page, ctx } = create();
    page.ngOnInit();
    page.ngOnDestroy();
    api.getInvestmentTransactions.mockClear();
    ctx.portfolioId = 999;
    expect(api.getInvestmentTransactions).not.toHaveBeenCalled();
  });

  describe('operaciones', () => {
    it('loadTransactions construye el filtro con lo que hay puesto y la página/tamaño actuales', () => {
      const { page } = create();
      page.ngOnInit();
      api.getInvestmentTransactions.mockClear();
      page.filterType = 'BUY';
      page.filterFrom = '2026-01-01';
      page.filterTo = '2026-12-31';
      page.filterSecurityId = 9;
      page.txPage = 2;
      page.txSize = 50;
      page.loadTransactions();
      expect(api.getInvestmentTransactions).toHaveBeenCalledWith(1, {
        type: 'BUY', from: '2026-01-01', to: '2026-12-31', securityId: 9,
      }, 2, 50);
    });

    it('loadTransactions no llama a la API sin cartera seleccionada', () => {
      const { page } = create({}, []);
      page.ngOnInit();
      api.getInvestmentTransactions.mockClear();
      page.loadTransactions();
      expect(api.getInvestmentTransactions).not.toHaveBeenCalled();
    });

    it('loadTransactions vuelca el contenido y el total de la página en el estado', () => {
      const tx = { id: 1 } as InvestmentTransactionView;
      api.getInvestmentTransactions = vi.fn().mockReturnValue(
        of({ content: [tx], page: 1, size: 10, totalElements: 42, totalPages: 5 }));
      const { page } = create({ getInvestmentTransactions: api.getInvestmentTransactions });
      page.ngOnInit();

      expect(page.transactions).toEqual([tx]);
      expect(page.txTotalElements).toBe(42);
    });

    it('onFiltersChange resetea a la primera página antes de recargar', () => {
      const { page } = create();
      page.ngOnInit();
      page.txPage = 3;
      api.getInvestmentTransactions.mockClear();
      page.onFiltersChange();
      expect(page.txPage).toBe(0);
      expect(api.getInvestmentTransactions).toHaveBeenCalledWith(1, expect.anything(), 0, 25);
    });

    it('onTxPageChange cambia de página y recarga', () => {
      const { page } = create();
      page.ngOnInit();
      api.getInvestmentTransactions.mockClear();
      page.onTxPageChange(3);
      expect(page.txPage).toBe(3);
      expect(api.getInvestmentTransactions).toHaveBeenCalledWith(1, expect.anything(), 3, 25);
    });

    it('onTxSizeChange cambia el tamaño de página, resetea a la primera página y recarga', () => {
      const { page } = create();
      page.ngOnInit();
      page.txPage = 3;
      api.getInvestmentTransactions.mockClear();
      page.onTxSizeChange(100);
      expect(page.txSize).toBe(100);
      expect(page.txPage).toBe(0);
      expect(api.getInvestmentTransactions).toHaveBeenCalledWith(1, expect.anything(), 0, 100);
    });

    it('onTxSizeChange hace una única llamada, nunca una con el tamaño antiguo (regresión: carrera de peticiones)', () => {
      const { page } = create();
      page.ngOnInit();
      api.getInvestmentTransactions.mockClear();
      page.onTxSizeChange(10);
      expect(api.getInvestmentTransactions).toHaveBeenCalledTimes(1);
      expect(api.getInvestmentTransactions).not.toHaveBeenCalledWith(1, expect.anything(), expect.anything(), 25);
    });

    it('editTransaction delega en el diálogo de la barra de herramientas', () => {
      const { page } = create();
      page.ngOnInit();
      const edit = vi.fn();
      page.toolbar = { edit } as unknown as InvestmentToolbar;
      const tx = { id: 1 } as InvestmentTransactionView;
      page.editTransaction(tx);
      expect(edit).toHaveBeenCalledWith(tx);
    });

    it('deleteTransaction pide confirmación y borra', () => {
      vi.spyOn(window, 'confirm').mockReturnValue(true);
      const { page } = create();
      page.ngOnInit();
      const tx: InvestmentTransactionView = {
        id: 5, type: 'BUY', tradeDate: '2026-01-01', securityId: 9, securityName: 'Empresa',
        quantity: 1, price: 100, amount: -100, currency: 'EUR', counterAmount: null,
      } as InvestmentTransactionView;
      page.deleteTransaction(tx);
      expect(api.deleteInvestmentTransaction).toHaveBeenCalledWith(5);
    });

    it('deleteTransaction no borra si no se confirma', () => {
      vi.spyOn(window, 'confirm').mockReturnValue(false);
      const { page } = create();
      page.ngOnInit();
      page.deleteTransaction({ id: 5, type: 'SELL' } as InvestmentTransactionView);
      expect(api.deleteInvestmentTransaction).not.toHaveBeenCalled();
    });
  });

  describe('dividendos', () => {
    it('loadIncome calcula los años disponibles, orden descendente', () => {
      const { page } = create();
      page.ngOnInit();
      expect(page.incomeYears).toEqual([2026, 2025]);
    });

    it('si el año seleccionado deja de existir, cae al más reciente disponible', () => {
      const { page } = create();
      page.incomeYear = 1999;
      page.ngOnInit();
      expect(page.incomeYear).toBe(2026);
    });

    it('incomeRows agrega por instrumento dentro del año seleccionado', () => {
      const { page } = create();
      page.ngOnInit();
      page.incomeYear = 2026;
      expect(page.incomeRows).toEqual([{ name: 'Empresa', gross: 10, withheld: 1.5, net: 8.5 }]);
    });

    it('incomeRows con "Todo" agrega todos los años', () => {
      const { page } = create();
      page.ngOnInit();
      page.incomeYear = 'all';
      expect(page.incomeRows[0].gross).toBe(15);
    });

    it('incomeTotals suma las filas', () => {
      const { page } = create();
      page.ngOnInit();
      page.incomeYear = 'all';
      expect(page.incomeTotals).toEqual({ gross: 15, withheld: 2.25, net: 12.75 });
    });

    it('feesTotal/taxesTotal filtran por el año seleccionado', () => {
      const { page } = create();
      page.ngOnInit();
      page.incomeYear = 2026;
      expect(page.feesTotal).toBe(2);
      expect(page.taxesTotal).toBe(0.5);
      page.incomeYear = 'all';
      expect(page.feesTotal).toBe(3);
    });
  });

  describe('pestañas y gráfico de dividendos', () => {
    it('setTab cambia de pestaña', () => {
      const { page } = create();
      page.ngOnInit();
      page.setTab('dividendos');
      expect(page.activeTab).toBe('dividendos');
    });

    it('dividendos agrupa por instrumento y por mes del año seleccionado', () => {
      const { page } = create();
      page.dividendCanvas = fakeCanvas();
      page.ngOnInit();
      (page as unknown as { viewReady: boolean }).viewReady = true;
      page.incomeYear = 2026;
      page.setTab('dividendos');
      (page as unknown as { renderCharts(): void }).renderCharts();
      const charts = (page as unknown as {
        charts: { config: { data: { labels: string[]; datasets: { label: string; data: number[] }[] } } }[]
      }).charts;
      const dividendChart = charts[0];
      expect(dividendChart.config.data.labels.length).toBe(12);
      expect(dividendChart.config.data.datasets[0].label).toBe('Empresa');
      expect(dividendChart.config.data.datasets[0].data[2]).toBe(10); // marzo, índice 2
    });

    it('ngOnDestroy destruye los gráficos', () => {
      const { page } = create();
      page.dividendCanvas = fakeCanvas();
      page.ngOnInit();
      (page as unknown as { viewReady: boolean }).viewReady = true;
      page.setTab('dividendos');
      (page as unknown as { renderCharts(): void }).renderCharts();
      const charts = (page as unknown as { charts: { destroy: ReturnType<typeof vi.fn> }[] }).charts;
      page.ngOnDestroy();
      charts.forEach(c => expect(c.destroy).toHaveBeenCalled());
    });
  });

  describe('importaciones (RF-11)', () => {
    it('activar la pestaña carga el historial de la cartera actual', () => {
      const { page } = create();
      page.ngOnInit();
      api.getImportHistory.mockClear();
      page.setTab('importaciones');
      expect(api.getImportHistory).toHaveBeenCalledWith(1, 0, 25);
    });

    it('no llama a la API sin cartera seleccionada', () => {
      const { page } = create({}, []);
      page.ngOnInit();
      api.getImportHistory.mockClear();
      page.setTab('importaciones');
      expect(api.getImportHistory).not.toHaveBeenCalled();
    });

    it('vuelca el contenido y el total de la página en el estado', () => {
      const record: ImportRecordView = {
        id: 1, importedAt: '2026-07-26T10:15:30Z', fileName: 'flex.csv',
        fromDate: '2026-01-01', toDate: '2026-06-30', imported: 12, duplicated: 3,
        errors: [{ section: 'Trades', reference: 'T-1', message: 'Instrumento desconocido' }],
        warnings: ['2026-03-01: venta sin posición suficiente'],
      };
      api.getImportHistory = vi.fn().mockReturnValue(
        of({ content: [record], page: 0, size: 25, totalElements: 1, totalPages: 1 }));
      const { page } = create({ getImportHistory: api.getImportHistory });
      page.ngOnInit();
      page.setTab('importaciones');

      expect(page.importHistory).toEqual([record]);
      expect(page.historyTotalElements).toBe(1);
    });

    it('onHistoryPageChange cambia de página y recarga', () => {
      const { page } = create();
      page.ngOnInit();
      page.setTab('importaciones');
      api.getImportHistory.mockClear();
      page.onHistoryPageChange(2);
      expect(page.historyPage).toBe(2);
      expect(api.getImportHistory).toHaveBeenCalledWith(1, 2, 25);
    });

    it('onHistorySizeChange cambia el tamaño, resetea a la primera página y recarga', () => {
      const { page } = create();
      page.ngOnInit();
      page.setTab('importaciones');
      page.historyPage = 3;
      api.getImportHistory.mockClear();
      page.onHistorySizeChange(50);
      expect(page.historySize).toBe(50);
      expect(page.historyPage).toBe(0);
      expect(api.getImportHistory).toHaveBeenCalledWith(1, 0, 50);
    });

    it('toggleHistoryDetail expande y colapsa el detalle de una fila', () => {
      const { page } = create();
      page.ngOnInit();
      expect(page.expandedHistoryId).toBeNull();
      page.toggleHistoryDetail(1);
      expect(page.expandedHistoryId).toBe(1);
      page.toggleHistoryDetail(1);
      expect(page.expandedHistoryId).toBeNull();
    });

    it('reloadImportHistoryIfActive solo recarga cuando la pestaña está activa', () => {
      const { page } = create();
      page.ngOnInit();
      api.getImportHistory.mockClear();
      page.reloadImportHistoryIfActive();
      expect(api.getImportHistory).not.toHaveBeenCalled();

      page.setTab('importaciones');
      api.getImportHistory.mockClear();
      page.reloadImportHistoryIfActive();
      expect(api.getImportHistory).toHaveBeenCalledWith(1, 0, 25);
    });

    it('cambiar de cartera con la pestaña activa recarga el historial de la nueva cartera', () => {
      const { page, ctx } = create();
      page.ngOnInit();
      page.setTab('importaciones');
      ctx.portfolios = [portfolio, { id: 2, name: 'Otra', baseCurrency: 'USD' }];
      api.getImportHistory.mockClear();

      ctx.portfolioId = 2;

      expect(api.getImportHistory).toHaveBeenCalledWith(2, 0, 25);
    });

    it('cambiar de cartera con otra pestaña activa no llama al historial de imports', () => {
      const { page, ctx } = create();
      page.ngOnInit();
      ctx.portfolios = [portfolio, { id: 2, name: 'Otra', baseCurrency: 'USD' }];
      api.getImportHistory.mockClear();

      ctx.portfolioId = 2;

      expect(api.getImportHistory).not.toHaveBeenCalled();
    });
  });
});
