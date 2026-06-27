package com.xroig.finance.budgets.infrastructure.persistence;

import com.xroig.finance.PostgresTestBase;
import com.xroig.finance.budgets.application.AnnualBudgetView;
import com.xroig.finance.budgets.application.AnnualBudgetView.AnnualRow;
import com.xroig.finance.model.Account;
import com.xroig.finance.model.Budget;
import com.xroig.finance.model.Category;
import com.xroig.finance.model.RecurringBudget;
import com.xroig.finance.model.RecurringBudgetAmount;
import com.xroig.finance.model.Transaction;
import com.xroig.finance.repository.AccountRepository;
import com.xroig.finance.repository.BudgetRepository;
import com.xroig.finance.repository.CategoryRepository;
import com.xroig.finance.repository.RecurringBudgetRepository;
import com.xroig.finance.repository.TransactionRepository;
import com.xroig.finance.shared.domain.TransactionType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Read-model test (Level 2) for the annual matrix assembled by {@link BudgetQueryAdapter}
 * against real PostgreSQL (stage H5a, replaces the legacy mocked {@code BudgetServiceTest}):
 * planned from a manual budget vs. recurrence, real movement sums, parent/child
 * aggregation with nested children, account vs. aggregate scope, and the income/expense
 * split sorted by name. Data is seeded through the legacy repositories, which map the same
 * tables the adapter reads.
 */
@Import(BudgetQueryAdapter.class)
class BudgetQueryAdapterMatrixTest extends PostgresTestBase {

    private static final int YEAR = 2024;

    @Autowired private BudgetQueryAdapter adapter;
    @Autowired private AccountRepository accountRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private BudgetRepository budgetRepository;
    @Autowired private RecurringBudgetRepository recurringRepository;
    @Autowired private TransactionRepository transactionRepository;

    @Test
    void leafCategory_plannedFromManualBudgetAndActualFromSums() {
        Account corriente = account("Corriente");
        Category comida = category("Comida", TransactionType.EXPENSE, corriente);
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
        Account corriente = account("Corriente");
        Category comunidad = category("Comunidad", TransactionType.EXPENSE, corriente);
        recurrence(comunidad, 0b0000_0000_0100, true, amount("80", "2024-01")); // month 3 active

        AnnualRow row = rowFor(adapter.annual(YEAR, corriente.getId()).expense(), comunidad.getId());

        assertThat(row.months().get(2).budget()).isEqualByComparingTo("80");
        assertThat(row.months().get(2).budgetId()).isNull();
        assertThat(row.months().get(1).budget()).isEqualByComparingTo("0"); // month 2 not active
    }

    @Test
    void manualBudgetOverridesRecurrence() {
        Account corriente = account("Corriente");
        Category comunidad = category("Comunidad", TransactionType.EXPENSE, corriente);
        recurrence(comunidad, 0b0000_0000_0100, true, amount("80", "2024-01"));
        budget(corriente, comunidad, 3, "120");

        AnnualRow row = rowFor(adapter.annual(YEAR, corriente.getId()).expense(), comunidad.getId());

        assertThat(row.months().get(2).budget()).isEqualByComparingTo("120");
    }

    @Test
    void parentRow_aggregatesOwnAndChildrenAndNestsChildren() {
        Account corriente = account("Corriente");
        Category hogar = category("Hogar", TransactionType.EXPENSE, corriente);
        Category luz = subcategory("Luz", hogar);
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
        Account corriente = account("Corriente");
        Category comida = category("Comida", TransactionType.EXPENSE, corriente);
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
        Account corriente = account("Corriente");
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

    private Account account(String name) {
        Account a = new Account();
        a.setName(name);
        a.setType("Banco");
        a.setInitialBalance(BigDecimal.ZERO);
        return accountRepository.save(a);
    }

    private Category category(String name, TransactionType type, Account account) {
        Category c = new Category();
        c.setName(name);
        c.setType(type);
        c.setColor("#000000");
        c.setAccount(account);
        return categoryRepository.save(c);
    }

    private Category subcategory(String name, Category parent) {
        Category c = new Category();
        c.setName(name);
        c.setType(parent.getType());
        c.setColor("#111111");
        c.setAccount(parent.getAccount());
        c.setParent(parent);
        return categoryRepository.save(c);
    }

    private void budget(Account account, Category category, int month, String amount) {
        Budget b = new Budget();
        b.setAccount(account);
        b.setCategory(category);
        b.setYear(YEAR);
        b.setMonth(month);
        b.setAmount(new BigDecimal(amount));
        budgetRepository.save(b);
    }

    private void expense(Account account, Category category, LocalDate date, String amount) {
        Transaction t = new Transaction();
        t.setType(TransactionType.EXPENSE);
        t.setAmount(new BigDecimal(amount));
        t.setAccount(account);
        t.setCategory(category);
        t.setDate(date);
        transactionRepository.save(t);
    }

    private void recurrence(Category category, int monthsMask, boolean active, RecurringBudgetAmount... amounts) {
        RecurringBudget r = new RecurringBudget();
        r.setCategory(category);
        r.setMonths(monthsMask);
        r.setActive(active);
        for (RecurringBudgetAmount a : amounts) {
            a.setRecurringBudget(r);
            r.getAmounts().add(a);
        }
        recurringRepository.save(r);
    }

    private RecurringBudgetAmount amount(String value, String yearMonth) {
        RecurringBudgetAmount a = new RecurringBudgetAmount();
        a.setAmount(new BigDecimal(value));
        a.setValidoDesde(LocalDate.parse(yearMonth + "-01"));
        return a;
    }
}
