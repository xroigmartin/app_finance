package com.xroig.finance.budgets.infrastructure.persistence;

import com.xroig.finance.PostgresTestBase;
import com.xroig.finance.budgets.application.AnnualBudgetView;
import com.xroig.finance.budgets.application.AnnualBudgetView.AnnualRow;
import com.xroig.finance.budgets.domain.MonthsMask;
import com.xroig.finance.budgets.domain.RecurrenceAmount;
import com.xroig.finance.budgets.domain.RecurringBudget;
import com.xroig.finance.budgets.domain.RecurringBudgetRepository;
import com.xroig.finance.categories.domain.CategoryId;
import com.xroig.finance.accounts.infrastructure.persistence.AccountJpaEntity;
import com.xroig.finance.budgets.infrastructure.persistence.BudgetJpaEntity;
import com.xroig.finance.categories.infrastructure.persistence.CategoryJpaEntity;
import com.xroig.finance.transactions.infrastructure.persistence.TransactionJpaEntity;
import com.xroig.finance.accounts.infrastructure.persistence.AccountJpaRepository;
import com.xroig.finance.budgets.infrastructure.persistence.BudgetJpaRepository;
import com.xroig.finance.categories.infrastructure.persistence.CategoryJpaRepository;
import com.xroig.finance.transactions.infrastructure.persistence.TransactionJpaRepository;
import com.xroig.finance.shared.domain.Money;
import com.xroig.finance.shared.domain.TransactionType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Read-model test (Level 2) for the annual matrix assembled by {@link BudgetQueryAdapter}
 * against real PostgreSQL (stage H5a, replaces the legacy mocked {@code BudgetServiceTest}):
 * planned from a manual budget vs. recurrence, real movement sums, parent/child
 * aggregation with nested children, account vs. aggregate scope, and the income/expense
 * split sorted by name. Budgets/movements are seeded through their contexts' JPA
 * repositories (the same tables the adapter reads); recurrences through the budgets
 * persistence adapter.
 */
@Import({BudgetQueryAdapter.class, RecurringBudgetPersistenceAdapter.class, RecurringBudgetJpaMapper.class})
class BudgetQueryAdapterMatrixTest extends PostgresTestBase {

    private static final int YEAR = 2024;

    @Autowired private BudgetQueryAdapter adapter;
    @Autowired private AccountJpaRepository accountRepository;
    @Autowired private CategoryJpaRepository categoryRepository;
    @Autowired private BudgetJpaRepository budgetRepository;
    @Autowired private RecurringBudgetRepository recurringRepository;
    @Autowired private TransactionJpaRepository transactionRepository;

    @Test
    void leafCategory_plannedFromManualBudgetAndActualFromSums() {
        AccountJpaEntity corriente = account("Corriente");
        CategoryJpaEntity comida = category("Comida", TransactionType.EXPENSE, corriente);
        budget(corriente, comida, 3, "100");
        expense(corriente, comida, LocalDate.of(YEAR, 3, 10), "50");

        AnnualRow row = rowFor(adapter.annual(YEAR, corriente.getId()).expense(), comida.getId());

        assertThat(row.editable()).isTrue();
        assertThat(row.months().get(2).budget()).isEqualByComparingTo("100");
        assertThat(row.months().get(2).actual()).isEqualByComparingTo("50");
        assertThat(row.months().get(2).budgetId()).isNotNull();
        assertThat(row.months().get(0).budget()).isEqualByComparingTo("0");
        assertThat(row.months().get(0).budgetId()).isNull();
    }

    @Test
    void recurrence_fillsPlannedWhenNoManualBudget() {
        AccountJpaEntity corriente = account("Corriente");
        CategoryJpaEntity comunidad = category("Comunidad", TransactionType.EXPENSE, corriente);
        recurrence(comunidad, List.of(3), true, ramount("80", "2024-01")); // month 3 active

        AnnualRow row = rowFor(adapter.annual(YEAR, corriente.getId()).expense(), comunidad.getId());

        assertThat(row.months().get(2).budget()).isEqualByComparingTo("80");
        assertThat(row.months().get(2).budgetId()).isNull();
        assertThat(row.months().get(1).budget()).isEqualByComparingTo("0"); // month 2 not active
    }

    @Test
    void manualBudgetOverridesRecurrence() {
        AccountJpaEntity corriente = account("Corriente");
        CategoryJpaEntity comunidad = category("Comunidad", TransactionType.EXPENSE, corriente);
        recurrence(comunidad, List.of(3), true, ramount("80", "2024-01"));
        budget(corriente, comunidad, 3, "120");

        AnnualRow row = rowFor(adapter.annual(YEAR, corriente.getId()).expense(), comunidad.getId());

        assertThat(row.months().get(2).budget()).isEqualByComparingTo("120");
    }

    @Test
    void parentRow_aggregatesOwnAndChildrenAndNestsChildren() {
        AccountJpaEntity corriente = account("Corriente");
        CategoryJpaEntity hogar = category("Hogar", TransactionType.EXPENSE, corriente);
        CategoryJpaEntity luz = subcategory("Luz", hogar);
        budget(corriente, luz, 1, "50");
        expense(corriente, hogar, LocalDate.of(YEAR, 1, 5), "10"); // legacy direct movement on parent
        expense(corriente, luz, LocalDate.of(YEAR, 1, 6), "30");

        AnnualBudgetView result = adapter.annual(YEAR, corriente.getId());

        assertThat(result.expense()).extracting(AnnualRow::categoryId).containsExactly(hogar.getId());
        AnnualRow parentRow = result.expense().get(0);
        assertThat(parentRow.editable()).isFalse();
        assertThat(parentRow.months().get(0).budget()).isEqualByComparingTo("50"); // 0 own + 50 child
        assertThat(parentRow.months().get(0).actual()).isEqualByComparingTo("40"); // 10 own + 30 child
        assertThat(parentRow.children()).extracting(AnnualRow::categoryId).containsExactly(luz.getId());
        AnnualRow childRow = parentRow.children().get(0);
        assertThat(childRow.editable()).isTrue();
        assertThat(childRow.months().get(0).budget()).isEqualByComparingTo("50");
        assertThat(childRow.months().get(0).actual()).isEqualByComparingTo("30");
    }

    @Test
    void aggregateScope_usesAllAccountsAndCarriesNoBudgetIds() {
        AccountJpaEntity corriente = account("Corriente");
        CategoryJpaEntity comida = category("Comida", TransactionType.EXPENSE, corriente);
        budget(corriente, comida, 3, "100");
        expense(corriente, comida, LocalDate.of(YEAR, 3, 10), "50");

        AnnualBudgetView result = adapter.annual(YEAR, null);

        assertThat(result.accountId()).isNull();
        AnnualRow row = rowFor(result.expense(), comida.getId());
        assertThat(row.months().get(2).budget()).isEqualByComparingTo("100");
        assertThat(row.months().get(2).actual()).isEqualByComparingTo("50");
        assertThat(row.months().get(2).budgetId()).isNull(); // read-only in aggregate scope
    }

    @Test
    void rowsSplitByTypeAndSortedByNameCaseInsensitive() {
        AccountJpaEntity corriente = account("Corriente");
        category("Zeta", TransactionType.EXPENSE, corriente);
        category("alfa", TransactionType.EXPENSE, corriente);
        category("Nómina", TransactionType.INCOME, corriente);

        AnnualBudgetView result = adapter.annual(YEAR, corriente.getId());

        assertThat(result.expense()).extracting(AnnualRow::category).containsExactly("alfa", "Zeta");
        assertThat(result.income()).extracting(AnnualRow::category).containsExactly("Nómina");
    }

    // ---- helpers ----

    private static AnnualRow rowFor(List<AnnualRow> rows, long categoryId) {
        return rows.stream().filter(r -> r.categoryId() == categoryId).findFirst().orElseThrow();
    }

    private AccountJpaEntity account(String name) {
        AccountJpaEntity a = new AccountJpaEntity();
        a.setName(name);
        a.setType("Banco");
        a.setInitialBalance(BigDecimal.ZERO);
        return accountRepository.save(a);
    }

    private CategoryJpaEntity category(String name, TransactionType type, AccountJpaEntity account) {
        CategoryJpaEntity c = new CategoryJpaEntity();
        c.setName(name);
        c.setType(type);
        c.setColor("#000000");
        c.setAccount(account);
        return categoryRepository.save(c);
    }

    private CategoryJpaEntity subcategory(String name, CategoryJpaEntity parent) {
        CategoryJpaEntity c = new CategoryJpaEntity();
        c.setName(name);
        c.setType(parent.getType());
        c.setColor("#111111");
        c.setAccount(parent.getAccount());
        c.setParent(parent);
        return categoryRepository.save(c);
    }

    private void budget(AccountJpaEntity account, CategoryJpaEntity category, int month, String amount) {
        BudgetJpaEntity b = new BudgetJpaEntity();
        b.setAccount(account);
        b.setCategory(category);
        b.setYear(YEAR);
        b.setMonth(month);
        b.setAmount(new BigDecimal(amount));
        budgetRepository.save(b);
    }

    private void expense(AccountJpaEntity account, CategoryJpaEntity category, LocalDate date, String amount) {
        TransactionJpaEntity t = new TransactionJpaEntity();
        t.setType(TransactionType.EXPENSE);
        t.setAmount(new BigDecimal(amount));
        t.setAccount(account);
        t.setCategory(category);
        t.setDate(date);
        transactionRepository.save(t);
    }

    private void recurrence(CategoryJpaEntity category, List<Integer> months, boolean active, RecurrenceAmount... amounts) {
        recurringRepository.save(RecurringBudget.create(new CategoryId(category.getId()),
                MonthsMask.ofMonths(months), active, List.of(amounts)));
    }

    private RecurrenceAmount ramount(String value, String yearMonth) {
        return new RecurrenceAmount(Money.of(value), YearMonth.parse(yearMonth));
    }
}
