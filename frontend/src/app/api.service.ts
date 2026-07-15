import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import {
  Account, AccountComparison, AnnualBudget, BalancePoint, Budget, BudgetRequest, BudgetStatus,
  Category, CategoryAmount, CategoryRule, FlexImportResult, ImportResult, InvestmentIncome, InvestmentPerformance,
  InvestmentSecurity,
  InvestmentTransactionFilter, InvestmentTransactionRequest, InvestmentTransactionView,
  InvestmentsSummary, MonthlyPoint, Portfolio, PortfolioSummary, PositionView, RecurringBudget,
  RuleRequest, RuleSaveResult, Summary, Transaction, TransactionRequest, Transfer,
  TransferRequest, ValuationPoint
} from './models';

@Injectable({ providedIn: 'root' })
export class ApiService {
  private http = inject(HttpClient);
  private base = '/api';

  // Dashboard
  getSummary(year?: number, month?: number, accountId?: number): Observable<Summary> {
    const params: Record<string, number> = {};
    if (year) params['year'] = year;
    if (month) params['month'] = month;
    if (accountId) params['accountId'] = accountId;
    return this.http.get<Summary>(`${this.base}/dashboard/summary`, { params });
  }

  getExpensesByCategory(year?: number, month?: number, accountId?: number): Observable<CategoryAmount[]> {
    const params: Record<string, number> = {};
    if (year) params['year'] = year;
    if (month) params['month'] = month;
    if (accountId) params['accountId'] = accountId;
    return this.http.get<CategoryAmount[]>(`${this.base}/dashboard/expenses-by-category`, { params });
  }

  getMonthly(months = 12, year?: number, month?: number, accountId?: number): Observable<MonthlyPoint[]> {
    const params: Record<string, number> = { months };
    if (year) params['year'] = year;
    if (month) params['month'] = month;
    if (accountId) params['accountId'] = accountId;
    return this.http.get<MonthlyPoint[]>(`${this.base}/dashboard/monthly`, { params });
  }

  getIncomeByCategory(year?: number, month?: number, accountId?: number): Observable<CategoryAmount[]> {
    const params: Record<string, number> = {};
    if (year) params['year'] = year;
    if (month) params['month'] = month;
    if (accountId) params['accountId'] = accountId;
    return this.http.get<CategoryAmount[]>(`${this.base}/dashboard/income-by-category`, { params });
  }

  getMonthlyBalance(months = 12, year?: number, month?: number, accountId?: number): Observable<BalancePoint[]> {
    const params: Record<string, number> = { months };
    if (year) params['year'] = year;
    if (month) params['month'] = month;
    if (accountId) params['accountId'] = accountId;
    return this.http.get<BalancePoint[]>(`${this.base}/dashboard/monthly-balance`, { params });
  }

  getAccountComparison(months = 12, year?: number, month?: number): Observable<AccountComparison> {
    const params: Record<string, number> = { months };
    if (year) params['year'] = year;
    if (month) params['month'] = month;
    return this.http.get<AccountComparison>(`${this.base}/dashboard/by-account`, { params });
  }

  getBudgetStatus(year?: number, month?: number, accountId?: number): Observable<BudgetStatus[]> {
    const params: Record<string, number> = {};
    if (year) params['year'] = year;
    if (month) params['month'] = month;
    if (accountId) params['accountId'] = accountId;
    return this.http.get<BudgetStatus[]>(`${this.base}/dashboard/budgets`, { params });
  }

  // Budgets
  getAnnualBudget(year: number, accountId?: number): Observable<AnnualBudget> {
    const params: Record<string, number> = { year };
    if (accountId) params['accountId'] = accountId;
    return this.http.get<AnnualBudget>(`${this.base}/budgets/annual`, { params });
  }

  getBudgets(year: number, month: number, accountId?: number): Observable<Budget[]> {
    const params: Record<string, number> = { year, month };
    if (accountId) params['accountId'] = accountId;
    return this.http.get<Budget[]>(`${this.base}/budgets`, { params });
  }

  copyBudgets(fromYear: number, fromMonth: number, toYear: number, toMonth: number): Observable<Budget[]> {
    return this.http.post<Budget[]>(`${this.base}/budgets/copy`, { fromYear, fromMonth, toYear, toMonth });
  }

  createBudget(req: BudgetRequest): Observable<Budget> {
    return this.http.post<Budget>(`${this.base}/budgets`, req);
  }

  updateBudget(id: number, req: BudgetRequest): Observable<Budget> {
    return this.http.put<Budget>(`${this.base}/budgets/${id}`, req);
  }

  deleteBudget(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/budgets/${id}`);
  }

  // Transfers
  getTransfers(from?: string, to?: string, accountId?: number): Observable<Transfer[]> {
    const params: Record<string, string | number> = {};
    if (from) params['from'] = from;
    if (to) params['to'] = to;
    if (accountId) params['accountId'] = accountId;
    return this.http.get<Transfer[]>(`${this.base}/transfers`, { params });
  }

  createTransfer(req: TransferRequest): Observable<Transfer> {
    return this.http.post<Transfer>(`${this.base}/transfers`, req);
  }

  updateTransfer(id: number, req: TransferRequest): Observable<Transfer> {
    return this.http.put<Transfer>(`${this.base}/transfers/${id}`, req);
  }

  deleteTransfer(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/transfers/${id}`);
  }

  // Transactions
  getTransactions(from?: string, to?: string, accountId?: number, categoryId?: number): Observable<Transaction[]> {
    const params: Record<string, string | number> = {};
    if (from) params['from'] = from;
    if (to) params['to'] = to;
    if (accountId) params['accountId'] = accountId;
    if (categoryId) params['categoryId'] = categoryId;
    return this.http.get<Transaction[]>(`${this.base}/transactions`, { params });
  }

  getRecentTransactions(): Observable<Transaction[]> {
    return this.http.get<Transaction[]>(`${this.base}/transactions/recent`);
  }

  createTransaction(req: TransactionRequest): Observable<Transaction> {
    return this.http.post<Transaction>(`${this.base}/transactions`, req);
  }

  updateTransaction(id: number, req: TransactionRequest): Observable<Transaction> {
    return this.http.put<Transaction>(`${this.base}/transactions/${id}`, req);
  }

  deleteTransaction(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/transactions/${id}`);
  }

  importTransactions(file: File, accountId?: number): Observable<ImportResult> {
    const data = new FormData();
    data.append('file', file);
    const params: Record<string, number> = {};
    if (accountId) params['accountId'] = accountId;
    return this.http.post<ImportResult>(`${this.base}/transactions/import`, data, { params });
  }

  importTransfers(file: File): Observable<ImportResult> {
    const data = new FormData();
    data.append('file', file);
    return this.http.post<ImportResult>(`${this.base}/transfers/import`, data);
  }

  // Accounts
  getAccounts(): Observable<Account[]> {
    return this.http.get<Account[]>(`${this.base}/accounts`);
  }

  createAccount(account: Account): Observable<Account> {
    return this.http.post<Account>(`${this.base}/accounts`, account);
  }

  updateAccount(id: number, account: Account): Observable<Account> {
    return this.http.put<Account>(`${this.base}/accounts/${id}`, account);
  }

  deleteAccount(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/accounts/${id}`);
  }

  // Categories
  getCategories(): Observable<Category[]> {
    return this.http.get<Category[]>(`${this.base}/categories`);
  }

  createCategory(category: Category): Observable<Category> {
    return this.http.post<Category>(`${this.base}/categories`, category);
  }

  updateCategory(id: number, category: Category): Observable<Category> {
    return this.http.put<Category>(`${this.base}/categories/${id}`, category);
  }

  deleteCategory(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/categories/${id}`);
  }

  // Category recurrence (planned recurring payment)
  getRecurrence(categoryId: number): Observable<RecurringBudget | null> {
    return this.http
      .get<RecurringBudget>(`${this.base}/categories/${categoryId}/recurrence`)
      .pipe(catchError(() => of(null)));
  }

  saveRecurrence(categoryId: number, body: RecurringBudget): Observable<RecurringBudget> {
    return this.http.put<RecurringBudget>(`${this.base}/categories/${categoryId}/recurrence`, body);
  }

  deleteRecurrence(categoryId: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/categories/${categoryId}/recurrence`);
  }

  // Investments
  getPortfolios(): Observable<Portfolio[]> {
    return this.http.get<Portfolio[]>(`${this.base}/investments/portfolios`);
  }

  createPortfolio(portfolio: Portfolio): Observable<Portfolio> {
    return this.http.post<Portfolio>(`${this.base}/investments/portfolios`, portfolio);
  }

  updatePortfolio(id: number, portfolio: Portfolio): Observable<Portfolio> {
    return this.http.put<Portfolio>(`${this.base}/investments/portfolios/${id}`, portfolio);
  }

  deletePortfolio(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/investments/portfolios/${id}`);
  }

  getPositions(portfolioId: number): Observable<PositionView[]> {
    return this.http.get<PositionView[]>(`${this.base}/investments/portfolios/${portfolioId}/positions`);
  }

  getPortfolioSummary(portfolioId: number): Observable<PortfolioSummary> {
    return this.http.get<PortfolioSummary>(`${this.base}/investments/portfolios/${portfolioId}/summary`);
  }

  getValuationHistory(portfolioId: number): Observable<ValuationPoint[]> {
    return this.http.get<ValuationPoint[]>(`${this.base}/investments/portfolios/${portfolioId}/valuation-history`);
  }

  getInvestmentIncome(portfolioId: number): Observable<InvestmentIncome> {
    return this.http.get<InvestmentIncome>(`${this.base}/investments/portfolios/${portfolioId}/income`);
  }

  getInvestmentPerformance(portfolioId: number): Observable<InvestmentPerformance> {
    return this.http.get<InvestmentPerformance>(
      `${this.base}/investments/portfolios/${portfolioId}/performance`);
  }

  getInvestmentsSummary(): Observable<InvestmentsSummary> {
    return this.http.get<InvestmentsSummary>(`${this.base}/investments/summary`);
  }

  getSecurities(): Observable<InvestmentSecurity[]> {
    return this.http.get<InvestmentSecurity[]>(`${this.base}/investments/securities`);
  }

  getInvestmentTransactions(portfolioId: number,
                            filter: InvestmentTransactionFilter = {}): Observable<InvestmentTransactionView[]> {
    const params: Record<string, string | number> = {};
    if (filter.type) params['type'] = filter.type;
    if (filter.from) params['from'] = filter.from;
    if (filter.to) params['to'] = filter.to;
    if (filter.securityId) params['securityId'] = filter.securityId;
    return this.http.get<InvestmentTransactionView[]>(
      `${this.base}/investments/portfolios/${portfolioId}/transactions`, { params });
  }

  createInvestmentTransaction(portfolioId: number,
                              req: InvestmentTransactionRequest): Observable<InvestmentTransactionView> {
    return this.http.post<InvestmentTransactionView>(
      `${this.base}/investments/portfolios/${portfolioId}/transactions`, req);
  }

  updateInvestmentTransaction(id: number,
                              req: InvestmentTransactionRequest): Observable<InvestmentTransactionView> {
    return this.http.put<InvestmentTransactionView>(`${this.base}/investments/transactions/${id}`, req);
  }

  deleteInvestmentTransaction(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/investments/transactions/${id}`);
  }

  importFlexReport(portfolioId: number, file: File): Observable<FlexImportResult> {
    const data = new FormData();
    data.append('file', file);
    return this.http.post<FlexImportResult>(
      `${this.base}/investments/portfolios/${portfolioId}/import`, data);
  }

  // Category rules
  getCategoryRules(): Observable<CategoryRule[]> {
    return this.http.get<CategoryRule[]>(`${this.base}/category-rules`);
  }

  createCategoryRule(req: RuleRequest): Observable<RuleSaveResult> {
    return this.http.post<RuleSaveResult>(`${this.base}/category-rules`, req);
  }

  updateCategoryRule(id: number, req: RuleRequest): Observable<RuleSaveResult> {
    return this.http.put<RuleSaveResult>(`${this.base}/category-rules/${id}`, req);
  }

  deleteCategoryRule(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/category-rules/${id}`);
  }
}
