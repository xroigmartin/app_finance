package com.xroig.finance.transactions.infrastructure.persistence;

import com.xroig.finance.PostgresTestBase;
import com.xroig.finance.accounts.domain.Account;
import com.xroig.finance.accounts.domain.AccountId;
import com.xroig.finance.accounts.infrastructure.persistence.AccountJpaMapper;
import com.xroig.finance.accounts.infrastructure.persistence.AccountPersistenceAdapter;
import com.xroig.finance.categories.domain.Category;
import com.xroig.finance.categories.domain.CategoryId;
import com.xroig.finance.categories.domain.CategoryScope;
import com.xroig.finance.categories.infrastructure.persistence.CategoryJpaMapper;
import com.xroig.finance.categories.infrastructure.persistence.CategoryPersistenceAdapter;
import com.xroig.finance.shared.domain.Money;
import com.xroig.finance.shared.domain.TransactionType;
import com.xroig.finance.transactions.application.TransactionView;
import com.xroig.finance.transactions.domain.Transaction;
import com.xroig.finance.transactions.domain.TransactionId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Adapter test (Level 2) against real PostgreSQL: the {@link TransactionPersistenceAdapter}
 * round-trip through {@link TransactionJpaMapper} (the pure aggregate survives, account/
 * category/refund referenced by id), the refunded-amount sum, and the read-side
 * {@link TransactionQueryAdapter} assembling the nested {@link TransactionView}.
 */
@Import({TransactionPersistenceAdapter.class, TransactionQueryAdapter.class, TransactionJpaMapper.class,
        CategoryPersistenceAdapter.class, CategoryJpaMapper.class,
        AccountPersistenceAdapter.class, AccountJpaMapper.class})
class TransactionPersistenceAdapterTest extends PostgresTestBase {

    private static final LocalDate JAN = LocalDate.of(2024, 1, 15);
    private static final LocalDate WIDE_FROM = LocalDate.of(1970, 1, 1);
    private static final LocalDate WIDE_TO = LocalDate.of(2999, 12, 31);

    @Autowired private TransactionPersistenceAdapter adapter;
    @Autowired private TransactionQueryAdapter queries;
    @Autowired private CategoryPersistenceAdapter categories;
    @Autowired private AccountPersistenceAdapter accounts;

    private AccountId acc;
    private CategoryId cat;

    private void seedRefs() {
        acc = accounts.save(Account.create("Corriente", "Banco", Money.of("100"))).id();
        cat = categories.save(Category.createTopLevel("Comida", TransactionType.EXPENSE, "#abc",
                CategoryScope.boundTo(acc))).id();
    }

    private Transaction newExpense(String amount) {
        return adapter.save(Transaction.record(JAN, Money.of(amount), "Compra", TransactionType.EXPENSE, acc, cat));
    }

    @Test
    void save_normal_roundTripsThroughTheMapper() {
        seedRefs();

        Transaction saved = newExpense("50");

        assertThat(saved.id()).isNotNull();
        assertThat(adapter.findById(saved.id())).hasValueSatisfying(t -> {
            assertThat(t.amount()).isEqualTo(Money.of("50"));
            assertThat(t.type()).isEqualTo(TransactionType.EXPENSE);
            assertThat(t.accountId()).isEqualTo(acc);
            assertThat(t.categoryId()).isEqualTo(cat);
            assertThat(t.isRefund()).isFalse();
        });
    }

    @Test
    void save_refund_linksOriginalAndSumsRefundedAmount() {
        seedRefs();
        Transaction original = newExpense("100");
        Transaction reloaded = adapter.findById(original.id()).orElseThrow();

        Transaction refund = adapter.save(
                Transaction.refundOf(JAN, Money.of("30"), "Dev", reloaded, Money.zero()));

        assertThat(refund.refundOfId()).isEqualTo(original.id());
        assertThat(adapter.refundedAmountFor(original.id(), null)).isEqualTo(Money.of("30"));
        // Excluding the refund itself yields zero.
        assertThat(adapter.refundedAmountFor(original.id(), refund.id())).isEqualTo(Money.zero());
    }

    @Test
    void save_existingMovement_updatesInPlace() {
        seedRefs();
        Transaction saved = newExpense("50");

        saved.changeToNormal(LocalDate.of(2024, 2, 2), Money.of("75"), "Editado",
                TransactionType.INCOME, acc, cat);
        Transaction reSaved = adapter.save(saved);

        assertThat(reSaved.id()).isEqualTo(saved.id());
        assertThat(adapter.findById(saved.id())).hasValueSatisfying(t -> {
            assertThat(t.amount()).isEqualTo(Money.of("75"));
            assertThat(t.type()).isEqualTo(TransactionType.INCOME);
        });
    }

    @Test
    void delete_removesTheRow() {
        seedRefs();
        Transaction saved = newExpense("50");

        adapter.deleteById(saved.id());

        assertThat(adapter.findById(saved.id())).isEmpty();
    }

    @Test
    void queryAdapter_searchAssemblesNestedViewsNewestFirst() {
        seedRefs();
        Transaction original = newExpense("100");
        adapter.save(Transaction.refundOf(LocalDate.of(2024, 2, 1), Money.of("30"), "Dev",
                adapter.findById(original.id()).orElseThrow(), Money.zero()));

        List<TransactionView> views = queries.search(WIDE_FROM, WIDE_TO, acc.value(), null);

        assertThat(views).hasSize(2);
        // Newest first: the February refund leads.
        assertThat(views.get(0).refundOf()).isNotNull();
        assertThat(views.get(0).refundOf().id()).isEqualTo(original.id().value());
        TransactionView expenseView = views.get(1);
        assertThat(expenseView.account().id()).isEqualTo(acc.value());
        assertThat(expenseView.account().name()).isEqualTo("Corriente");
        assertThat(expenseView.category().id()).isEqualTo(cat.value());
        assertThat(expenseView.category().name()).isEqualTo("Comida");
        assertThat(expenseView.refundOf()).isNull();
    }

    @Test
    void queryAdapter_recentAndFindById() {
        seedRefs();
        Transaction saved = newExpense("50");

        assertThat(queries.recent()).extracting(TransactionView::id).contains(saved.id().value());
        assertThat(queries.findById(saved.id())).hasValueSatisfying(v ->
                assertThat(v.amount()).isEqualByComparingTo("50"));
    }

    @Test
    void queryAdapter_searchFiltersByCategory() {
        seedRefs();
        CategoryId other = categories.save(Category.createTopLevel("Ocio", TransactionType.EXPENSE, "#def",
                CategoryScope.boundTo(acc))).id();
        newExpense("50");
        adapter.save(Transaction.record(JAN, Money.of("20"), "x", TransactionType.EXPENSE, acc, other));

        assertThat(queries.search(WIDE_FROM, WIDE_TO, acc.value(), cat.value()))
                .singleElement().satisfies(v -> assertThat(v.category().id()).isEqualTo(cat.value()));
    }
}
