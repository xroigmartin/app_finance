package com.xroig.finance.reporting.application;

import com.xroig.finance.reporting.application.AccountCatalogQuery.ReportAccount;
import com.xroig.finance.reporting.application.AccountComparisonView.AccountSeriesView;
import com.xroig.finance.reporting.application.BudgetCatalogQuery.ReportBudget;
import com.xroig.finance.reporting.application.SummaryView.AccountBalanceView;
import com.xroig.finance.reporting.application.port.DashboardReports;
import com.xroig.finance.shared.domain.TransactionType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/**
 * Application service for the reporting context (read-only, CQRS). Keeps the dashboard
 * aggregation maths the legacy {@code DashboardService} had — balances (initial + net
 * movements + transfers in/out), savings, deltas, yield percentages, net-worth series,
 * per-account comparison and budget roll-up — reading the raw figures through the outbound
 * query ports and never touching the write aggregates.
 */
@Service
@Transactional(readOnly = true)
public class ReportingService implements DashboardReports {

    private final MovementAggregateQuery movements;
    private final TransferAggregateQuery transfers;
    private final AccountCatalogQuery accounts;
    private final BudgetCatalogQuery budgets;

    public ReportingService(MovementAggregateQuery movements, TransferAggregateQuery transfers,
                            AccountCatalogQuery accounts, BudgetCatalogQuery budgets) {
        this.movements = movements;
        this.transfers = transfers;
        this.accounts = accounts;
        this.budgets = budgets;
    }

    @Override
    public SummaryView summary(int year, int month, Long accountId) {
        YearMonth current = YearMonth.of(year, month);
        LocalDate from = current.atDay(1);
        LocalDate to = current.atEndOfMonth();
        LocalDate yearFrom = LocalDate.of(year, 1, 1);
        LocalDate yearTo = LocalDate.of(year, 12, 31);

        BigDecimal income = movements.sumByType(TransactionType.INCOME, from, to, accountId);
        BigDecimal expense = movements.sumByType(TransactionType.EXPENSE, from, to, accountId);
        BigDecimal yearIncome = movements.sumByType(TransactionType.INCOME, yearFrom, yearTo, accountId);
        BigDecimal yearExpense = movements.sumByType(TransactionType.EXPENSE, yearFrom, yearTo, accountId);

        List<ReportAccount> all = accounts.all();
        // Balances at the end of the selected month.
        List<AccountBalanceView> accountViews = all.stream()
                .map(a -> new AccountBalanceView(a.id(), a.name(), a.type(), balanceUntil(a, to)))
                .toList();

        BigDecimal totalBalance = accountViews.stream()
                .filter(a -> accountId == null || a.id().equals(accountId))
                .map(AccountBalanceView::balance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal monthStart = totalBalanceUntil(all, accountId, from.minusDays(1));
        BigDecimal yearStart = totalBalanceUntil(all, accountId, yearFrom.minusDays(1));
        BigDecimal yearEnd = totalBalanceUntil(all, accountId, yearTo);

        BigDecimal monthSavings = income.subtract(expense);
        BigDecimal yearSavings = yearIncome.subtract(yearExpense);
        BigDecimal monthDelta = totalBalance.subtract(monthStart);
        BigDecimal yearDelta = yearEnd.subtract(yearStart);

        return new SummaryView(totalBalance, income, expense, monthSavings,
                yearIncome, yearExpense, yearSavings,
                monthDelta, yearDelta,
                yieldPercent(monthDelta, monthStart), yieldPercent(yearDelta, yearStart),
                yieldPercent(monthSavings, monthStart), yieldPercent(yearSavings, yearStart),
                accountViews);
    }

    private BigDecimal balanceUntil(ReportAccount account, LocalDate until) {
        return account.initialBalance()
                .add(movements.netByAccountUntil(account.id(), until))
                .add(transfers.inUntil(account.id(), until))
                .subtract(transfers.outUntil(account.id(), until));
    }

    private BigDecimal totalBalanceUntil(List<ReportAccount> all, Long accountId, LocalDate until) {
        return all.stream()
                .filter(a -> accountId == null || a.id().equals(accountId))
                .map(a -> balanceUntil(a, until))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Amount over the starting balance, as a percentage; null if the base is not positive. */
    private BigDecimal yieldPercent(BigDecimal amount, BigDecimal base) {
        if (base.signum() <= 0) {
            return null;
        }
        return amount.multiply(BigDecimal.valueOf(100)).divide(base, 2, RoundingMode.HALF_UP);
    }

    @Override
    public List<CategoryAmountView> expensesByCategory(int year, int month, Long accountId) {
        return byCategory(TransactionType.EXPENSE, year, month, accountId);
    }

    @Override
    public List<CategoryAmountView> incomeByCategory(int year, int month, Long accountId) {
        return byCategory(TransactionType.INCOME, year, month, accountId);
    }

    private List<CategoryAmountView> byCategory(TransactionType type, int year, int month, Long accountId) {
        YearMonth ym = YearMonth.of(year, month);
        return movements.shareByCategory(type, ym.atDay(1), ym.atEndOfMonth(), accountId).stream()
                .map(s -> new CategoryAmountView(s.category(), s.color(), s.amount()))
                .toList();
    }

    @Override
    public List<MonthlyPointView> monthlyEvolution(int months, YearMonth end, Long accountId) {
        List<MonthlyPointView> points = new ArrayList<>();
        for (int i = months - 1; i >= 0; i--) {
            YearMonth ym = end.minusMonths(i);
            LocalDate from = ym.atDay(1);
            LocalDate to = ym.atEndOfMonth();
            points.add(new MonthlyPointView(ym.toString(),
                    movements.sumByType(TransactionType.INCOME, from, to, accountId),
                    movements.sumByType(TransactionType.EXPENSE, from, to, accountId)));
        }
        return points;
    }

    @Override
    public List<BalancePointView> monthlyBalance(int months, YearMonth end, Long accountId) {
        List<ReportAccount> all = accounts.all();
        List<BalancePointView> points = new ArrayList<>();
        for (int i = months - 1; i >= 0; i--) {
            YearMonth ym = end.minusMonths(i);
            points.add(new BalancePointView(ym.toString(),
                    totalBalanceUntil(all, accountId, ym.atEndOfMonth())));
        }
        return points;
    }

    @Override
    public AccountComparisonView accountComparison(int months, YearMonth end) {
        List<String> labels = new ArrayList<>();
        for (int i = months - 1; i >= 0; i--) {
            labels.add(end.minusMonths(i).toString());
        }
        List<AccountSeriesView> series = accounts.all().stream()
                .map(a -> {
                    List<BigDecimal> income = new ArrayList<>();
                    List<BigDecimal> expense = new ArrayList<>();
                    for (int i = months - 1; i >= 0; i--) {
                        YearMonth ym = end.minusMonths(i);
                        LocalDate from = ym.atDay(1);
                        LocalDate to = ym.atEndOfMonth();
                        income.add(movements.sumByType(TransactionType.INCOME, from, to, a.id()));
                        expense.add(movements.sumByType(TransactionType.EXPENSE, from, to, a.id()));
                    }
                    return new AccountSeriesView(a.id(), a.name(), income, expense);
                })
                .toList();
        return new AccountComparisonView(labels, series);
    }

    @Override
    public List<BudgetStatusView> budgetStatus(int year, int month, Long accountId) {
        YearMonth ym = YearMonth.of(year, month);
        LocalDate from = ym.atDay(1);
        LocalDate to = ym.atEndOfMonth();
        return budgets.forMonth(year, month, accountId).stream()
                .map(b -> {
                    // Each budget is scoped to its account, so spending is too; budgets sit on
                    // top-level categories, so roll up subcategories.
                    BigDecimal spent = movements.spentByCategoryTree(b.categoryId(), from, to, b.accountId());
                    return new BudgetStatusView(b.budgetId(), b.accountId(), b.accountName(),
                            b.categoryId(), b.categoryName(), b.categoryColor(),
                            b.amount(), spent, b.amount().subtract(spent));
                })
                .sorted((a, b) -> b.spent().compareTo(a.spent()))
                .toList();
    }
}
