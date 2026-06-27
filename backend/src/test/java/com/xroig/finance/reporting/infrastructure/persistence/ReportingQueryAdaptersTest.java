package com.xroig.finance.reporting.infrastructure.persistence;

import com.xroig.finance.model.Account;
import com.xroig.finance.model.Category;
import com.xroig.finance.reporting.application.AccountCatalogQuery.ReportAccount;
import com.xroig.finance.reporting.application.BudgetCatalogQuery.ReportBudget;
import com.xroig.finance.reporting.application.MovementAggregateQuery.CategoryShare;
import com.xroig.finance.repository.AccountRepository;
import com.xroig.finance.repository.BudgetRepository;
import com.xroig.finance.repository.TransactionRepository;
import com.xroig.finance.repository.TransferRepository;
import com.xroig.finance.shared.domain.TransactionType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static com.xroig.finance.Fixtures.account;
import static com.xroig.finance.Fixtures.budget;
import static com.xroig.finance.Fixtures.category;
import static com.xroig.finance.Fixtures.eur;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the reporting query adapters: each delegates to the legacy aggregation
 * query and maps the result onto the reporting outbound-port types. Pure Mockito.
 */
@ExtendWith(MockitoExtension.class)
class ReportingQueryAdaptersTest {

    @Mock private TransactionRepository transactions;
    @Mock private TransferRepository transfers;
    @Mock private AccountRepository accounts;
    @Mock private BudgetRepository budgets;

    private static final LocalDate FROM = LocalDate.of(2024, 3, 1);
    private static final LocalDate TO = LocalDate.of(2024, 3, 31);

    @Test
    void movementAggregate_delegatesAndMapsCategoryShares() {
        MovementAggregateQueryAdapter adapter = new MovementAggregateQueryAdapter(transactions);
        when(transactions.sumByTypeAndPeriod(TransactionType.INCOME, FROM, TO, 1L)).thenReturn(eur("400"));
        when(transactions.netTotalByAccountUntil(1L, TO)).thenReturn(eur("500"));
        when(transactions.sumByCategoryTreeAndPeriod(10L, FROM, TO, 1L)).thenReturn(eur("150"));
        when(transactions.sumByCategory(TransactionType.EXPENSE, FROM, TO, 1L)).thenReturn(
                List.<Object[]>of(new Object[] {"Comida", "#ff0000", eur("120")}));

        assertThat(adapter.sumByType(TransactionType.INCOME, FROM, TO, 1L)).isEqualByComparingTo("400");
        assertThat(adapter.netByAccountUntil(1L, TO)).isEqualByComparingTo("500");
        assertThat(adapter.spentByCategoryTree(10L, FROM, TO, 1L)).isEqualByComparingTo("150");
        assertThat(adapter.shareByCategory(TransactionType.EXPENSE, FROM, TO, 1L))
                .containsExactly(new CategoryShare("Comida", "#ff0000", eur("120")));
    }

    @Test
    void transferAggregate_delegatesInAndOut() {
        TransferAggregateQueryAdapter adapter = new TransferAggregateQueryAdapter(transfers);
        when(transfers.totalInUntil(1L, TO)).thenReturn(eur("70"));
        when(transfers.totalOutUntil(1L, TO)).thenReturn(eur("30"));

        assertThat(adapter.inUntil(1L, TO)).isEqualByComparingTo("70");
        assertThat(adapter.outUntil(1L, TO)).isEqualByComparingTo("30");
    }

    @Test
    void accountCatalog_mapsEntitiesToReportAccounts() {
        when(accounts.findAll()).thenReturn(List.of(account(1, "Corriente", eur("1000"))));

        List<ReportAccount> all = new AccountCatalogQueryAdapter(accounts).all();

        assertThat(all).singleElement().satisfies(a -> {
            assertThat(a.id()).isEqualTo(1L);
            assertThat(a.name()).isEqualTo("Corriente");
            assertThat(a.type()).isEqualTo("CORRIENTE");
            assertThat(a.initialBalance()).isEqualByComparingTo("1000");
        });
    }

    @Test
    void budgetCatalog_scopedToAccountFlattensFields() {
        Account corriente = account(1, "Corriente", eur("1000"));
        Category comida = category(10, "Comida", TransactionType.EXPENSE, corriente);
        when(budgets.findByAccountIdAndYearAndMonth(1L, 2024, 3))
                .thenReturn(List.of(budget(500L, corriente, comida, 2024, 3, eur("200"))));

        List<ReportBudget> rows = new BudgetCatalogQueryAdapter(budgets).forMonth(2024, 3, 1L);

        assertThat(rows).singleElement().satisfies(b -> {
            assertThat(b.budgetId()).isEqualTo(500L);
            assertThat(b.accountId()).isEqualTo(1L);
            assertThat(b.accountName()).isEqualTo("Corriente");
            assertThat(b.categoryId()).isEqualTo(10L);
            assertThat(b.categoryName()).isEqualTo("Comida");
            assertThat(b.amount()).isEqualByComparingTo("200");
        });
    }

    @Test
    void budgetCatalog_aggregateScopeQueriesAllAccounts() {
        Account corriente = account(1, "Corriente", eur("1000"));
        Category comida = category(10, "Comida", TransactionType.EXPENSE, corriente);
        when(budgets.findByYearAndMonth(2024, 3))
                .thenReturn(List.of(budget(500L, corriente, comida, 2024, 3, eur("200"))));

        List<ReportBudget> rows = new BudgetCatalogQueryAdapter(budgets).forMonth(2024, 3, null);

        assertThat(rows).hasSize(1);
        verify(budgets).findByYearAndMonth(2024, 3);
    }
}
