package com.xroig.finance.reporting.infrastructure.persistence;

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
import com.xroig.finance.reporting.application.MovementView;
import com.xroig.finance.shared.domain.Money;
import com.xroig.finance.shared.domain.Page;
import com.xroig.finance.shared.domain.TransactionType;
import com.xroig.finance.transactions.domain.Transaction;
import com.xroig.finance.transactions.infrastructure.persistence.TransactionJpaMapper;
import com.xroig.finance.transactions.infrastructure.persistence.TransactionPersistenceAdapter;
import com.xroig.finance.transfers.domain.Transfer;
import com.xroig.finance.transfers.infrastructure.persistence.TransferJpaMapper;
import com.xroig.finance.transfers.infrastructure.persistence.TransferPersistenceAdapter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Adapter test (Level 2) against real PostgreSQL: {@link MovementQueryAdapter} combines
 * transactions + transfers into one chronologically ordered, paginated feed
 * ("Movimientos") — a plain client-side merge of two independently-paginated lists can't
 * paginate correctly across sources, so the page's keys are resolved with a single JPQL
 * {@code union all} query (ordering + LIMIT/OFFSET at the database), then each side's
 * rows are hydrated from their own repository.
 */
@Import({MovementQueryAdapter.class, TransactionPersistenceAdapter.class, TransactionJpaMapper.class,
        TransferPersistenceAdapter.class, TransferJpaMapper.class,
        CategoryPersistenceAdapter.class, CategoryJpaMapper.class,
        AccountPersistenceAdapter.class, AccountJpaMapper.class})
class MovementQueryAdapterTest extends PostgresTestBase {

    private static final LocalDate WIDE_FROM = LocalDate.of(1970, 1, 1);
    private static final LocalDate WIDE_TO = LocalDate.of(2999, 12, 31);

    @Autowired private MovementQueryAdapter adapter;
    @Autowired private TransactionPersistenceAdapter transactions;
    @Autowired private TransferPersistenceAdapter transfers;
    @Autowired private CategoryPersistenceAdapter categories;
    @Autowired private AccountPersistenceAdapter accounts;

    private AccountId accA;
    private AccountId accB;
    private CategoryId cat;

    private void seedRefs() {
        accA = accounts.save(Account.create("Corriente", "Banco", Money.of("1000"))).id();
        accB = accounts.save(Account.create("Ahorro", "Banco", Money.of("500"))).id();
        cat = categories.save(Category.createTopLevel("Comida", TransactionType.EXPENSE, "#abc",
                CategoryScope.boundTo(accA))).id();
    }

    private void expense(LocalDate date, String amount) {
        transactions.save(Transaction.record(date, Money.of(amount), "Gasto", TransactionType.EXPENSE, accA, cat));
    }

    private void transfer(LocalDate date, String amount) {
        transfers.save(Transfer.create(date, Money.of(amount), "Traspaso", accA, accB));
    }

    @Test
    void search_ordersTransactionsAndTransfersTogetherAcrossSources() {
        seedRefs();
        expense(LocalDate.of(2024, 1, 10), "10");
        transfer(LocalDate.of(2024, 1, 20), "20");
        expense(LocalDate.of(2024, 1, 30), "30");

        Page<MovementView> page = adapter.search(WIDE_FROM, WIDE_TO, null, null, 0, 10);

        assertThat(page.content()).extracting(MovementView::source).containsExactly("tx", "tr", "tx");
        assertThat(page.content().get(0).tx().amount()).isEqualByComparingTo("30");
        assertThat(page.content().get(1).tr().amount()).isEqualByComparingTo("20");
        assertThat(page.totalElements()).isEqualTo(3);
    }

    @Test
    void search_paginatesTheCombinedFeed() {
        seedRefs();
        expense(LocalDate.of(2024, 1, 10), "10");
        transfer(LocalDate.of(2024, 1, 20), "20");
        expense(LocalDate.of(2024, 1, 30), "30");

        Page<MovementView> firstPage = adapter.search(WIDE_FROM, WIDE_TO, null, null, 0, 2);
        Page<MovementView> secondPage = adapter.search(WIDE_FROM, WIDE_TO, null, null, 1, 2);

        assertThat(firstPage.content()).hasSize(2);
        assertThat(firstPage.totalPages()).isEqualTo(2);
        assertThat(secondPage.content()).hasSize(1);
    }

    @Test
    void search_categoryFilterHidesTransfersEntirely() {
        seedRefs();
        expense(LocalDate.of(2024, 1, 10), "10");
        transfer(LocalDate.of(2024, 1, 20), "20");

        Page<MovementView> page = adapter.search(WIDE_FROM, WIDE_TO, null, cat.value(), 0, 10);

        assertThat(page.content()).extracting(MovementView::source).containsExactly("tx");
        assertThat(page.totalElements()).isEqualTo(1);
    }

    @Test
    void search_accountFilterMatchesEitherTransferLeg() {
        seedRefs();
        expense(LocalDate.of(2024, 1, 10), "10");
        transfer(LocalDate.of(2024, 1, 20), "20");

        Page<MovementView> byOrigin = adapter.search(WIDE_FROM, WIDE_TO, accA.value(), null, 0, 10);
        Page<MovementView> byDestination = adapter.search(WIDE_FROM, WIDE_TO, accB.value(), null, 0, 10);

        assertThat(byOrigin.totalElements()).isEqualTo(2);
        assertThat(byDestination.totalElements()).isEqualTo(1);
        assertThat(byDestination.content()).extracting(MovementView::source).containsExactly("tr");
    }
}
