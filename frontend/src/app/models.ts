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

/** One row of the combined, paginated "Movimientos" feed (transactions + transfers). */
export interface Movement {
  source: 'tx' | 'tr';
  date: string;
  id: number;
  tx: Transaction | null;
  tr: Transfer | null;
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

// ── Inversiones ────────────────────────────────────────────────────────────

export interface Portfolio {
  id?: number;
  name: string;
  /** ISO 4217; inmutable tras la creación (los snapshots RN-7a apuntan a ella). */
  baseCurrency: string;
}

/** Posición valorada (view CQRS). Importes monetarios en la divisa base de la cartera. */
export interface PositionView {
  securityId: number;
  isin: string;
  name: string;
  ticker: string | null;
  /** Divisa de cotización del instrumento (la de marketPrice). */
  currency: string;
  quantity: number;
  /** Null en posiciones cerradas o negativas (RN-4). */
  averageCost: number | null;
  costBasis: number;
  marketPrice: number | null;
  quoteDate: string | null;
  marketValue: number;
  latentPnl: number;
  latentPnlPercent: number | null;
  /** Peso sobre el valor total (posiciones + efectivo); null si el total es 0. */
  weight: number | null;
  /** True cuando no hay cotización y la posición se valora a coste (aviso RN-6). */
  pricedAtCost: boolean;
}

/** KPIs de cabecera de una cartera; importes en su divisa base salvo cashByCurrency. */
export interface PortfolioSummary {
  portfolioId: number;
  name: string;
  baseCurrency: string;
  totalValue: number;
  /** Fecha de valoración (la cotización más antigua usada); null sin cotizaciones. */
  valuationDate: string | null;
  netContributions: number;
  latentPnl: number;
  latentPnlPercent: number | null;
  cashByCurrency: Record<string, number>;
  dividendsThisYear: number;
}

/** Punto de la serie de evolución: valor y aportado acumulado a una fecha. */
export interface ValuationPoint {
  date: string;
  value: number;
  contributed: number;
}

/** Resumen global multi-cartera en EUR (tarjeta del dashboard, RF-10). */
export interface InvestmentsSummary {
  totalValue: number;
  valuationDate: string | null;
  portfolios: PortfolioValue[];
}

export interface PortfolioValue {
  portfolioId: number;
  name: string;
  baseCurrency: string;
  value: number;
  valuationDate: string | null;
}

export type InvestmentTransactionType =
  | 'BUY' | 'SELL' | 'DIVIDEND' | 'INTEREST' | 'FEE' | 'TAX' | 'TRADE_TAX'
  | 'SPLIT' | 'DEPOSIT' | 'WITHDRAWAL' | 'FX_TRADE';

export const INVESTMENT_TYPE_LABELS: Record<InvestmentTransactionType, string> = {
  BUY: 'Compra', SELL: 'Venta', DIVIDEND: 'Dividendo', INTEREST: 'Interés',
  FEE: 'Comisión', TAX: 'Retención', TRADE_TAX: 'Tasa de compraventa',
  SPLIT: 'Split', DEPOSIT: 'Aportación', WITHDRAWAL: 'Retirada',
  FX_TRADE: 'Conversión de divisa'
};

/** Instrumento del catálogo (identidad ISIN+divisa; alta automática en import). */
export interface InvestmentSecurity {
  id: number;
  isin: string;
  currency: string;
  name: string;
  ticker: string | null;
  type: string | null;
  exchange: string | null;
  figi: string | null;
}

/** Una operación de cartera (listado y formulario de alta/edición, RF-2). */
export interface InvestmentTransactionView {
  id: number;
  type: InvestmentTransactionType;
  tradeDate: string;
  securityId: number | null;
  securityName: string | null;
  quantity: number | null;
  price: number | null;
  amount: number;
  currency: string;
  counterAmount: number | null;
  counterCurrency: string | null;
  fee: number | null;
  feeCurrency: string | null;
  tax: number | null;
  taxCurrency: string | null;
  fxRateToBase: number | null;
  description: string | null;
  /** Nulo en apuntes manuales; prefijado ORD-/CT-/FTT-/CA- en importados (RN-10). */
  externalId: string | null;
}

export interface InvestmentTransactionRequest {
  type: InvestmentTransactionType;
  tradeDate: string;
  securityId?: number | null;
  quantity?: number | null;
  price?: number | null;
  amount: number;
  currency: string;
  counterAmount?: number | null;
  counterCurrency?: string | null;
  fee?: number | null;
  feeCurrency?: string | null;
  tax?: number | null;
  taxCurrency?: string | null;
  fxRateToBase?: number | null;
  description?: string | null;
}

export interface InvestmentTransactionFilter {
  type?: InvestmentTransactionType;
  from?: string;
  to?: string;
  securityId?: number;
}

/** Página de un listado paginado por el backend (Operaciones, Movimientos). */
export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

/** Renta de un instrumento en un mes (RF-7); importes en la divisa base de la cartera. */
export interface IncomeEntry {
  /** Nulos para intereses sin instrumento (interés del broker). */
  securityId: number | null;
  name: string | null;
  /** Mes "YYYY-MM". */
  month: string;
  gross: number;
  withheld: number;
  net: number;
}

/** Agregado de un mes (comisiones o retenciones pagadas), magnitud positiva. */
export interface MonthAmount {
  month: string;
  amount: number;
}

/** Rentas de la cartera (RF-7): dividendos/intereses + comisiones/retenciones por mes. */
export interface InvestmentIncome {
  portfolioId: number;
  baseCurrency: string;
  incomes: IncomeEntry[];
  fees: MonthAmount[];
  taxes: MonthAmount[];
}

/** Rentabilidad de una posición abierta (RN-8); porcentajes, null si no calculable. */
export interface PositionPerformance {
  securityId: number;
  name: string;
  /** TWR acumulada del periodo observado, en %. */
  twrPercent: number | null;
  /** XIRR anualizada, en %. */
  xirrPercent: number | null;
}

/** Rentabilidad de la cartera (RN-8): TWR acumulada y XIRR anual, total y por posición. */
export interface InvestmentPerformance {
  portfolioId: number;
  baseCurrency: string;
  valuationDate: string | null;
  twrPercent: number | null;
  xirrPercent: number | null;
  positions: PositionPerformance[];
}

/** Fila ilegible/no soportada/inválida del import Flex (§8). */
export interface FlexRowError {
  section: string;
  reference: string | null;
  message: string;
}

/** Resumen del import Flex: ok / duplicadas / errores / warnings (RF-4, RN-4). */
export interface FlexImportResult {
  imported: number;
  duplicated: number;
  errors: FlexRowError[];
  warnings: string[];
}
