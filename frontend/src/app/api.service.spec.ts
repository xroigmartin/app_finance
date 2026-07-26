import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ApiService } from './api.service';
import {
  Account, BudgetRequest, Category, InvestmentTransactionRequest, Portfolio,
  RecurringBudget, RuleRequest, TransactionRequest, TransferRequest
} from './models';

describe('ApiService', () => {
  let service: ApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(ApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  describe('dashboard', () => {
    it('getSummary sin filtros no manda parámetros', () => {
      service.getSummary().subscribe();
      const req = http.expectOne(r => r.url === '/api/dashboard/summary');
      expect(req.request.params.keys().length).toBe(0);
      req.flush({});
    });

    it('getSummary con año/mes/cuenta los manda como query params', () => {
      service.getSummary(2026, 7, 3).subscribe();
      const req = http.expectOne(r => r.url === '/api/dashboard/summary');
      expect(req.request.params.get('year')).toBe('2026');
      expect(req.request.params.get('month')).toBe('7');
      expect(req.request.params.get('accountId')).toBe('3');
      req.flush({});
    });

    it('getExpensesByCategory', () => {
      service.getExpensesByCategory(2026, 7).subscribe();
      http.expectOne('/api/dashboard/expenses-by-category?year=2026&month=7').flush([]);
    });

    it('getIncomeByCategory', () => {
      service.getIncomeByCategory(2026, 7).subscribe();
      http.expectOne('/api/dashboard/income-by-category?year=2026&month=7').flush([]);
    });

    it('getMonthly usa 12 meses por defecto', () => {
      service.getMonthly().subscribe();
      const req = http.expectOne(r => r.url === '/api/dashboard/monthly');
      expect(req.request.params.get('months')).toBe('12');
      req.flush([]);
    });

    it('getMonthlyBalance', () => {
      service.getMonthlyBalance(6, 2026, 7, 3).subscribe();
      const req = http.expectOne(r => r.url === '/api/dashboard/monthly-balance');
      expect(req.request.params.get('months')).toBe('6');
      expect(req.request.params.get('accountId')).toBe('3');
      req.flush([]);
    });

    it('getAccountComparison no acepta cuenta (compara todas)', () => {
      service.getAccountComparison(12, 2026, 7).subscribe();
      const req = http.expectOne(r => r.url === '/api/dashboard/by-account');
      expect(req.request.params.get('months')).toBe('12');
      req.flush({ months: [], accounts: [] });
    });

    it('getBudgetStatus', () => {
      service.getBudgetStatus(2026, 7, 3).subscribe();
      http.expectOne('/api/dashboard/budgets?year=2026&month=7&accountId=3').flush([]);
    });
  });

  describe('budgets', () => {
    it('getAnnualBudget', () => {
      service.getAnnualBudget(2026, 3).subscribe();
      http.expectOne('/api/budgets/annual?year=2026&accountId=3').flush({});
    });

    it('getBudgets', () => {
      service.getBudgets(2026, 7).subscribe();
      http.expectOne('/api/budgets?year=2026&month=7').flush([]);
    });

    it('copyBudgets manda el rango en el body', () => {
      service.copyBudgets(2026, 6, 2026, 7).subscribe();
      const req = http.expectOne('/api/budgets/copy');
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual({ fromYear: 2026, fromMonth: 6, toYear: 2026, toMonth: 7 });
      req.flush([]);
    });

    it('createBudget', () => {
      const body: BudgetRequest = { accountId: 1, categoryId: 2, year: 2026, month: 7, amount: 100 };
      service.createBudget(body).subscribe();
      const req = http.expectOne('/api/budgets');
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual(body);
      req.flush({});
    });

    it('updateBudget', () => {
      const body: BudgetRequest = { accountId: 1, categoryId: 2, year: 2026, month: 7, amount: 150 };
      service.updateBudget(9, body).subscribe();
      const req = http.expectOne('/api/budgets/9');
      expect(req.request.method).toBe('PUT');
      req.flush({});
    });

    it('deleteBudget', () => {
      service.deleteBudget(9).subscribe();
      const req = http.expectOne('/api/budgets/9');
      expect(req.request.method).toBe('DELETE');
      req.flush(null);
    });
  });

  describe('transfers', () => {
    it('getTransfers sin filtros', () => {
      service.getTransfers().subscribe();
      const req = http.expectOne(r => r.url === '/api/transfers');
      expect(req.request.params.keys().length).toBe(0);
      req.flush([]);
    });

    it('getTransfers con rango de fechas y cuenta', () => {
      service.getTransfers('2026-01-01', '2026-12-31', 2).subscribe();
      http.expectOne('/api/transfers?from=2026-01-01&to=2026-12-31&accountId=2').flush([]);
    });

    it('createTransfer', () => {
      const body: TransferRequest = {
        date: '2026-07-01', amount: 100, description: null, fromAccountId: 1, toAccountId: 2,
      };
      service.createTransfer(body).subscribe();
      const req = http.expectOne('/api/transfers');
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual(body);
      req.flush({});
    });

    it('updateTransfer', () => {
      const body: TransferRequest = {
        date: '2026-07-01', amount: 100, description: null, fromAccountId: 1, toAccountId: 2,
      };
      service.updateTransfer(5, body).subscribe();
      expect(http.expectOne('/api/transfers/5').request.method).toBe('PUT');
    });

    it('deleteTransfer', () => {
      service.deleteTransfer(5).subscribe();
      expect(http.expectOne('/api/transfers/5').request.method).toBe('DELETE');
    });
  });

  describe('transactions', () => {
    it('getTransactions sin filtros', () => {
      service.getTransactions().subscribe();
      const req = http.expectOne(r => r.url === '/api/transactions');
      expect(req.request.params.keys().length).toBe(0);
      req.flush([]);
    });

    it('getTransactions con todos los filtros', () => {
      service.getTransactions('2026-01-01', '2026-12-31', 1, 4).subscribe();
      http.expectOne('/api/transactions?from=2026-01-01&to=2026-12-31&accountId=1&categoryId=4').flush([]);
    });

    it('getRecentTransactions', () => {
      service.getRecentTransactions().subscribe();
      http.expectOne('/api/transactions/recent').flush([]);
    });

    it('getMovements sin filtros usa la página/tamaño por defecto', () => {
      service.getMovements().subscribe();
      const req = http.expectOne(r => r.url === '/api/movements');
      expect(req.request.params.get('page')).toBe('0');
      expect(req.request.params.get('size')).toBe('25');
      expect(req.request.params.keys().length).toBe(2);
      req.flush({ content: [], page: 0, size: 25, totalElements: 0, totalPages: 0 });
    });

    it('getMovements con todos los filtros y página/tamaño explícitos', () => {
      service.getMovements('2026-01-01', '2026-12-31', 1, 4, 2, 10).subscribe();
      http.expectOne(
        '/api/movements?page=2&size=10&from=2026-01-01&to=2026-12-31&accountId=1&categoryId=4',
      ).flush({ content: [], page: 2, size: 10, totalElements: 0, totalPages: 0 });
    });

    it('createTransaction', () => {
      const body: TransactionRequest = {
        date: '2026-07-01', amount: 10, description: 'Test', type: 'EXPENSE', accountId: 1, categoryId: 2,
      };
      service.createTransaction(body).subscribe();
      const req = http.expectOne('/api/transactions');
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual(body);
      req.flush({});
    });

    it('updateTransaction', () => {
      const body: TransactionRequest = {
        date: '2026-07-01', amount: 10, description: 'Test', type: 'EXPENSE', accountId: 1, categoryId: 2,
      };
      service.updateTransaction(7, body).subscribe();
      expect(http.expectOne('/api/transactions/7').request.method).toBe('PUT');
    });

    it('deleteTransaction', () => {
      service.deleteTransaction(7).subscribe();
      expect(http.expectOne('/api/transactions/7').request.method).toBe('DELETE');
    });

    it('importTransactions manda el fichero como FormData y la cuenta como query param', () => {
      const file = new File(['a,b'], 'movs.csv', { type: 'text/csv' });
      service.importTransactions(file, 3).subscribe();
      const req = http.expectOne(r => r.url === '/api/transactions/import');
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toBeInstanceOf(FormData);
      expect((req.request.body as FormData).get('file')).toBe(file);
      expect(req.request.params.get('accountId')).toBe('3');
      req.flush({});
    });

    it('importTransfers manda el fichero sin query params', () => {
      const file = new File(['a,b'], 'transfers.csv', { type: 'text/csv' });
      service.importTransfers(file).subscribe();
      const req = http.expectOne('/api/transfers/import');
      expect(req.request.body).toBeInstanceOf(FormData);
      req.flush({});
    });
  });

  describe('accounts', () => {
    it('getAccounts', () => {
      service.getAccounts().subscribe();
      http.expectOne('/api/accounts').flush([]);
    });

    it('createAccount', () => {
      const account: Account = { name: 'Cuenta', type: 'Banco', initialBalance: 0 };
      service.createAccount(account).subscribe();
      const req = http.expectOne('/api/accounts');
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual(account);
      req.flush({});
    });

    it('updateAccount', () => {
      const account: Account = { name: 'Cuenta', type: 'Banco', initialBalance: 0 };
      service.updateAccount(1, account).subscribe();
      expect(http.expectOne('/api/accounts/1').request.method).toBe('PUT');
    });

    it('deleteAccount', () => {
      service.deleteAccount(1).subscribe();
      expect(http.expectOne('/api/accounts/1').request.method).toBe('DELETE');
    });
  });

  describe('categories', () => {
    it('getCategories', () => {
      service.getCategories().subscribe();
      http.expectOne('/api/categories').flush([]);
    });

    it('createCategory', () => {
      const category: Category = { name: 'Ocio', type: 'EXPENSE', color: '#000000' };
      service.createCategory(category).subscribe();
      const req = http.expectOne('/api/categories');
      expect(req.request.method).toBe('POST');
      req.flush({});
    });

    it('updateCategory', () => {
      const category: Category = { name: 'Ocio', type: 'EXPENSE', color: '#000000' };
      service.updateCategory(4, category).subscribe();
      expect(http.expectOne('/api/categories/4').request.method).toBe('PUT');
    });

    it('deleteCategory', () => {
      service.deleteCategory(4).subscribe();
      expect(http.expectOne('/api/categories/4').request.method).toBe('DELETE');
    });
  });

  describe('recurrencia de categoría', () => {
    it('getRecurrence devuelve el cuerpo cuando existe', () => {
      let result: RecurringBudget | null | undefined;
      service.getRecurrence(4).subscribe(r => (result = r));
      const body: RecurringBudget = { months: [1, 2, 3], active: true, amounts: [] };
      http.expectOne('/api/categories/4/recurrence').flush(body);
      expect(result).toEqual(body);
    });

    it('getRecurrence traga el error (p.ej. 404, sin recurrencia) y emite null', () => {
      let result: RecurringBudget | null | undefined;
      service.getRecurrence(4).subscribe(r => (result = r));
      http.expectOne('/api/categories/4/recurrence')
        .flush('not found', { status: 404, statusText: 'Not Found' });
      expect(result).toBeNull();
    });

    it('saveRecurrence', () => {
      const body: RecurringBudget = { months: [1], active: true, amounts: [] };
      service.saveRecurrence(4, body).subscribe();
      const req = http.expectOne('/api/categories/4/recurrence');
      expect(req.request.method).toBe('PUT');
      expect(req.request.body).toEqual(body);
      req.flush(body);
    });

    it('deleteRecurrence', () => {
      service.deleteRecurrence(4).subscribe();
      expect(http.expectOne('/api/categories/4/recurrence').request.method).toBe('DELETE');
    });
  });

  describe('inversión', () => {
    it('getPortfolios', () => {
      service.getPortfolios().subscribe();
      http.expectOne('/api/investments/portfolios').flush([]);
    });

    it('createPortfolio', () => {
      const portfolio: Portfolio = { name: 'Cartera', baseCurrency: 'EUR' };
      service.createPortfolio(portfolio).subscribe();
      const req = http.expectOne('/api/investments/portfolios');
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual(portfolio);
      req.flush({});
    });

    it('updatePortfolio', () => {
      const portfolio: Portfolio = { name: 'Cartera', baseCurrency: 'EUR' };
      service.updatePortfolio(1, portfolio).subscribe();
      expect(http.expectOne('/api/investments/portfolios/1').request.method).toBe('PUT');
    });

    it('deletePortfolio', () => {
      service.deletePortfolio(1).subscribe();
      expect(http.expectOne('/api/investments/portfolios/1').request.method).toBe('DELETE');
    });

    it('getPositions', () => {
      service.getPositions(1).subscribe();
      http.expectOne('/api/investments/portfolios/1/positions').flush([]);
    });

    it('getPortfolioSummary', () => {
      service.getPortfolioSummary(1).subscribe();
      http.expectOne('/api/investments/portfolios/1/summary').flush({});
    });

    it('getValuationHistory', () => {
      service.getValuationHistory(1).subscribe();
      http.expectOne('/api/investments/portfolios/1/valuation-history').flush([]);
    });

    it('getInvestmentIncome', () => {
      service.getInvestmentIncome(1).subscribe();
      http.expectOne('/api/investments/portfolios/1/income').flush({});
    });

    it('getInvestmentPerformance', () => {
      service.getInvestmentPerformance(1).subscribe();
      http.expectOne('/api/investments/portfolios/1/performance').flush({});
    });

    it('getInvestmentsSummary', () => {
      service.getInvestmentsSummary().subscribe();
      http.expectOne('/api/investments/summary').flush({});
    });

    it('getSecurities', () => {
      service.getSecurities().subscribe();
      http.expectOne('/api/investments/securities').flush([]);
    });

    it('getInvestmentTransactions sin filtro usa la página/tamaño por defecto', () => {
      service.getInvestmentTransactions(1).subscribe();
      const req = http.expectOne(r => r.url === '/api/investments/portfolios/1/transactions');
      expect(req.request.params.get('page')).toBe('0');
      expect(req.request.params.get('size')).toBe('25');
      expect(req.request.params.keys().length).toBe(2);
      req.flush({ content: [], page: 0, size: 25, totalElements: 0, totalPages: 0 });
    });

    it('getInvestmentTransactions con filtro completo y página/tamaño explícitos', () => {
      service.getInvestmentTransactions(1,
        { type: 'BUY', from: '2026-01-01', to: '2026-12-31', securityId: 5 }, 2, 10)
        .subscribe();
      http.expectOne(
        '/api/investments/portfolios/1/transactions?page=2&size=10&type=BUY&from=2026-01-01&to=2026-12-31&securityId=5',
      ).flush({ content: [], page: 2, size: 10, totalElements: 0, totalPages: 0 });
    });

    it('createInvestmentTransaction', () => {
      const body: InvestmentTransactionRequest = {
        type: 'BUY', tradeDate: '2026-01-01', amount: -100, currency: 'EUR',
      };
      service.createInvestmentTransaction(1, body).subscribe();
      const req = http.expectOne('/api/investments/portfolios/1/transactions');
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual(body);
      req.flush({});
    });

    it('updateInvestmentTransaction', () => {
      const body: InvestmentTransactionRequest = {
        type: 'BUY', tradeDate: '2026-01-01', amount: -100, currency: 'EUR',
      };
      service.updateInvestmentTransaction(9, body).subscribe();
      expect(http.expectOne('/api/investments/transactions/9').request.method).toBe('PUT');
    });

    it('deleteInvestmentTransaction', () => {
      service.deleteInvestmentTransaction(9).subscribe();
      expect(http.expectOne('/api/investments/transactions/9').request.method).toBe('DELETE');
    });

    it('importFlexReport manda el fichero como FormData', () => {
      const file = new File(['<xml/>'], 'flex.xml', { type: 'text/xml' });
      service.importFlexReport(1, file).subscribe();
      const req = http.expectOne('/api/investments/portfolios/1/import');
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toBeInstanceOf(FormData);
      req.flush({});
    });

    it('getImportHistory sin página/tamaño usa los valores por defecto', () => {
      service.getImportHistory(1).subscribe();
      const req = http.expectOne(r => r.url === '/api/investments/portfolios/1/import-history');
      expect(req.request.params.get('page')).toBe('0');
      expect(req.request.params.get('size')).toBe('25');
      req.flush({ content: [], page: 0, size: 25, totalElements: 0, totalPages: 0 });
    });

    it('getImportHistory con página/tamaño explícitos y mapea la respuesta', () => {
      let result: { totalElements: number } | undefined;
      service.getImportHistory(1, 2, 10).subscribe(page => (result = page));
      const req = http.expectOne('/api/investments/portfolios/1/import-history?page=2&size=10');
      req.flush({
        content: [{
          id: 1, importedAt: '2026-07-26T10:15:30Z', fileName: 'flex.csv',
          fromDate: '2026-01-01', toDate: '2026-06-30', imported: 12, duplicated: 3,
          errors: [{ section: 'Trades', reference: 'T-1', message: 'Instrumento desconocido' }],
          warnings: ['2026-03-01: venta sin posición suficiente'],
        }],
        page: 2, size: 10, totalElements: 1, totalPages: 1,
      });
      expect(result?.totalElements).toBe(1);
    });
  });

  describe('reglas de categorización', () => {
    it('getCategoryRules', () => {
      service.getCategoryRules().subscribe();
      http.expectOne('/api/category-rules').flush([]);
    });

    it('createCategoryRule', () => {
      const body: RuleRequest = { pattern: 'MERCADONA', categoryId: 4 };
      service.createCategoryRule(body).subscribe();
      const req = http.expectOne('/api/category-rules');
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual(body);
      req.flush({ rule: {}, recategorized: 0 });
    });

    it('updateCategoryRule', () => {
      const body: RuleRequest = { pattern: 'MERCADONA', categoryId: 4 };
      service.updateCategoryRule(2, body).subscribe();
      expect(http.expectOne('/api/category-rules/2').request.method).toBe('PUT');
    });

    it('deleteCategoryRule', () => {
      service.deleteCategoryRule(2).subscribe();
      expect(http.expectOne('/api/category-rules/2').request.method).toBe('DELETE');
    });
  });
});
