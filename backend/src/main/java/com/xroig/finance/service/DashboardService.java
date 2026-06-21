package com.xroig.finance.service;

import com.xroig.finance.dto.DashboardDtos.AccountBalance;
import com.xroig.finance.dto.DashboardDtos.AccountComparison;
import com.xroig.finance.dto.DashboardDtos.AccountSeries;
import com.xroig.finance.dto.DashboardDtos.BalancePoint;
import com.xroig.finance.dto.DashboardDtos.BudgetStatus;
import com.xroig.finance.dto.DashboardDtos.CategoryAmount;
import com.xroig.finance.dto.DashboardDtos.MonthlyPoint;
import com.xroig.finance.dto.DashboardDtos.Summary;
import com.xroig.finance.model.TransactionType;
import com.xroig.finance.repository.AccountRepository;
import com.xroig.finance.repository.BudgetRepository;
import com.xroig.finance.repository.TransactionRepository;
import com.xroig.finance.repository.TransferRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class DashboardService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final TransferRepository transferRepository;
    private final BudgetRepository budgetRepository;

    public DashboardService(AccountRepository accountRepository,
                            TransactionRepository transactionRepository,
                            TransferRepository transferRepository,
                            BudgetRepository budgetRepository) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.transferRepository = transferRepository;
        this.budgetRepository = budgetRepository;
    }

    public Summary summary(int year, int month, Long accountId) {
        YearMonth current = YearMonth.of(year, month);
        LocalDate from = current.atDay(1);
        LocalDate to = current.atEndOfMonth();
        LocalDate yearFrom = LocalDate.of(year, 1, 1);
        LocalDate yearTo = LocalDate.of(year, 12, 31);

        BigDecimal income = transactionRepository.sumByTypeAndPeriod(TransactionType.INCOME, from, to, accountId);
        BigDecimal expense = transactionRepository.sumByTypeAndPeriod(TransactionType.EXPENSE, from, to, accountId);
        BigDecimal yearIncome = transactionRepository.sumByTypeAndPeriod(TransactionType.INCOME, yearFrom, yearTo, accountId);
        BigDecimal yearExpense = transactionRepository.sumByTypeAndPeriod(TransactionType.EXPENSE, yearFrom, yearTo, accountId);

        // Saldos a fin del mes seleccionado
        List<AccountBalance> accounts = accountRepository.findAll().stream()
                .map(a -> new AccountBalance(a.getId(), a.getName(), a.getType(),
                        balanceUntil(a, to)))
                .toList();

        BigDecimal totalBalance = accounts.stream()
                .filter(a -> accountId == null || a.id().equals(accountId))
                .map(AccountBalance::balance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal monthStart = totalBalanceUntil(accountId, from.minusDays(1));
        BigDecimal yearStart = totalBalanceUntil(accountId, yearFrom.minusDays(1));
        BigDecimal yearEnd = totalBalanceUntil(accountId, yearTo);

        BigDecimal monthSavings = income.subtract(expense);
        BigDecimal yearSavings = yearIncome.subtract(yearExpense);
        BigDecimal monthDelta = totalBalance.subtract(monthStart);
        BigDecimal yearDelta = yearEnd.subtract(yearStart);

        return new Summary(totalBalance, income, expense, monthSavings,
                yearIncome, yearExpense, yearSavings,
                monthDelta, yearDelta,
                yieldPercent(monthDelta, monthStart), yieldPercent(yearDelta, yearStart),
                yieldPercent(monthSavings, monthStart), yieldPercent(yearSavings, yearStart),
                accounts);
    }

    private BigDecimal balanceUntil(com.xroig.finance.model.Account account, LocalDate until) {
        return account.getInitialBalance()
                .add(transactionRepository.netTotalByAccountUntil(account.getId(), until))
                .add(transferRepository.totalInUntil(account.getId(), until))
                .subtract(transferRepository.totalOutUntil(account.getId(), until));
    }

    private BigDecimal totalBalanceUntil(Long accountId, LocalDate until) {
        return accountRepository.findAll().stream()
                .filter(a -> accountId == null || a.getId().equals(accountId))
                .map(a -> balanceUntil(a, until))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Amount over the starting balance, as a percentage; null if the base is not positive. */
    private BigDecimal yieldPercent(BigDecimal amount, BigDecimal base) {
        if (base.signum() <= 0) {
            return null;
        }
        return amount.multiply(BigDecimal.valueOf(100))
                .divide(base, 2, java.math.RoundingMode.HALF_UP);
    }

    public List<CategoryAmount> expensesByCategory(int year, int month, Long accountId) {
        return byCategory(TransactionType.EXPENSE, year, month, accountId);
    }

    public List<CategoryAmount> incomeByCategory(int year, int month, Long accountId) {
        return byCategory(TransactionType.INCOME, year, month, accountId);
    }

    private List<CategoryAmount> byCategory(TransactionType type, int year, int month, Long accountId) {
        YearMonth ym = YearMonth.of(year, month);
        return transactionRepository
                .sumByCategory(type, ym.atDay(1), ym.atEndOfMonth(), accountId)
                .stream()
                .map(row -> new CategoryAmount((String) row[0], (String) row[1], (BigDecimal) row[2]))
                .toList();
    }

    /** Net worth at the end of each of the {@code months} months ending at {@code end}. */
    public List<BalancePoint> monthlyBalance(int months, YearMonth end, Long accountId) {
        List<BalancePoint> points = new ArrayList<>();
        for (int i = months - 1; i >= 0; i--) {
            YearMonth ym = end.minusMonths(i);
            points.add(new BalancePoint(ym.toString(),
                    totalBalanceUntil(accountId, ym.atEndOfMonth())));
        }
        return points;
    }

    /** Per-account monthly income/expense series, for cross-account comparison. */
    public AccountComparison accountComparison(int months, YearMonth end) {
        List<String> labels = new ArrayList<>();
        for (int i = months - 1; i >= 0; i--) {
            labels.add(end.minusMonths(i).toString());
        }
        List<AccountSeries> series = accountRepository.findAll().stream()
                .map(a -> {
                    List<BigDecimal> income = new ArrayList<>();
                    List<BigDecimal> expense = new ArrayList<>();
                    for (int i = months - 1; i >= 0; i--) {
                        YearMonth ym = end.minusMonths(i);
                        LocalDate from = ym.atDay(1);
                        LocalDate to = ym.atEndOfMonth();
                        income.add(transactionRepository.sumByTypeAndPeriod(
                                TransactionType.INCOME, from, to, a.getId()));
                        expense.add(transactionRepository.sumByTypeAndPeriod(
                                TransactionType.EXPENSE, from, to, a.getId()));
                    }
                    return new AccountSeries(a.getId(), a.getName(), income, expense);
                })
                .toList();
        return new AccountComparison(labels, series);
    }

    public List<BudgetStatus> budgetStatus(int year, int month, Long accountId) {
        YearMonth ym = YearMonth.of(year, month);
        LocalDate from = ym.atDay(1);
        LocalDate to = ym.atEndOfMonth();
        List<com.xroig.finance.model.Budget> budgets = accountId != null
                ? budgetRepository.findByAccountIdAndYearAndMonth(accountId, year, month)
                : budgetRepository.findByYearAndMonth(year, month);
        return budgets.stream()
                .map(b -> {
                    // Each budget is scoped to its account, so spending is too.
                    // Budgets sit on top-level categories, so roll up subcategories.
                    BigDecimal spent = transactionRepository.sumByCategoryTreeAndPeriod(
                            b.getCategory().getId(), from, to, b.getAccount().getId());
                    return new BudgetStatus(b.getId(),
                            b.getAccount().getId(), b.getAccount().getName(),
                            b.getCategory().getId(),
                            b.getCategory().getName(), b.getCategory().getColor(),
                            b.getAmount(), spent, b.getAmount().subtract(spent));
                })
                .sorted((a, b) -> b.spent().compareTo(a.spent()))
                .toList();
    }

    /** Evolution of the {@code months} months ending at {@code end} (inclusive). */
    public List<MonthlyPoint> monthlyEvolution(int months, YearMonth end, Long accountId) {
        List<MonthlyPoint> points = new ArrayList<>();
        for (int i = months - 1; i >= 0; i--) {
            YearMonth ym = end.minusMonths(i);
            LocalDate from = ym.atDay(1);
            LocalDate to = ym.atEndOfMonth();
            points.add(new MonthlyPoint(ym.toString(),
                    transactionRepository.sumByTypeAndPeriod(TransactionType.INCOME, from, to, accountId),
                    transactionRepository.sumByTypeAndPeriod(TransactionType.EXPENSE, from, to, accountId)));
        }
        return points;
    }
}
