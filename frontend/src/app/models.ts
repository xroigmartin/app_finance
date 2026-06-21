export type TransactionType = 'INCOME' | 'EXPENSE';

export interface Account {
  id?: number;
  name: string;
  type: string;
  initialBalance: number;
}

export interface Category {
  id?: number;
  name: string;
  type: TransactionType;
  color: string;
  /** Null/undefined for global categories; otherwise the owning account. */
  account?: Account | null;
  /** Null/undefined for top-level categories; otherwise the parent category. */
  parent?: Category | null;
}

export interface Transaction {
  id?: number;
  date: string;
  amount: number;
  description: string | null;
  type: TransactionType;
  account: Account;
  category: Category;
  /** When set, this movement is a refund (total or partial) of that expense. */
  refundOf?: Transaction | null;
}

export interface TransactionRequest {
  date: string;
  amount: number;
  description: string | null;
  type: TransactionType;
  accountId: number;
  categoryId: number;
  /** When set, creates/updates this movement as a refund of that expense. */
  refundOfId?: number | null;
}

export interface Transfer {
  id?: number;
  date: string;
  amount: number;
  description: string | null;
  fromAccount: Account;
  toAccount: Account;
}

export interface TransferRequest {
  date: string;
  amount: number;
  description: string | null;
  fromAccountId: number;
  toAccountId: number;
}

export interface Budget {
  id?: number;
  account: Account;
  category: Category;
  year: number;
  month: number;
  amount: number;
}

export interface BudgetRequest {
  accountId: number;
  categoryId: number;
  year: number;
  month: number;
  amount: number;
}

export interface BudgetStatus {
  budgetId: number;
  accountId: number;
  account: string;
  categoryId: number;
  category: string;
  color: string;
  budget: number;
  spent: number;
  remaining: number;
}

/** One category in one month of the annual budget matrix. */
export interface MonthCell {
  /** Null when no budget exists yet for this cell (create vs. update). */
  budgetId: number | null;
  budget: number;
  actual: number;
}

export interface AnnualRow {
  categoryId: number;
  category: string;
  color: string;
  type: TransactionType;
  /**
   * True for leaf categories whose cells take a budget (top-level without
   * subcategories, or a subcategory). False for a parent row, whose months are
   * the aggregate of its children and is not budgeted directly.
   */
  editable: boolean;
  /** Always 12 entries, January to December. */
  months: MonthCell[];
  /** Nested subcategory rows; empty for leaf rows. */
  children: AnnualRow[];
}

export interface AnnualBudget {
  year: number;
  accountId: number | null;
  income: AnnualRow[];
  expense: AnnualRow[];
}

/** One effective-dated amount of a recurring budget. */
export interface RecurringAmount {
  id?: number;
  amount: number;
  /** Month from which the amount applies, as "YYYY-MM". */
  validoDesde: string;
}

/** Recurrence of a leaf, account-bound category that feeds the planned matrix. */
export interface RecurringBudget {
  categoryId?: number;
  /** Active months, 1 (January) to 12 (December). */
  months: number[];
  active: boolean;
  amounts: RecurringAmount[];
}

export interface CategoryRule {
  id?: number;
  pattern: string;
  category: Category;
}

export interface RuleRequest {
  pattern: string;
  categoryId: number;
}

export interface RuleSaveResult {
  rule: CategoryRule;
  recategorized: number;
}

export interface RowError {
  row: number;
  message: string;
}

export interface ImportResult {
  imported: number;
  duplicated: number;
  errors: RowError[];
}

export interface AccountBalance {
  id: number;
  name: string;
  type: string;
  balance: number;
}

export interface Summary {
  totalBalance: number;
  monthIncome: number;
  monthExpense: number;
  monthSavings: number;
  yearIncome: number;
  yearExpense: number;
  yearSavings: number;
  monthBalanceDelta: number;
  yearBalanceDelta: number;
  monthGrowthPct: number | null;
  yearGrowthPct: number | null;
  monthSavingsYieldPct: number | null;
  yearSavingsYieldPct: number | null;
  accounts: AccountBalance[];
}

export interface CategoryAmount {
  category: string;
  color: string;
  amount: number;
}

export interface MonthlyPoint {
  month: string;
  income: number;
  expense: number;
}

export interface BalancePoint {
  month: string;
  balance: number;
}

export interface AccountSeries {
  accountId: number;
  name: string;
  income: number[];
  expense: number[];
}

export interface AccountComparison {
  months: string[];
  accounts: AccountSeries[];
}
