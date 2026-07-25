package com.xroig.finance.categorization.infrastructure.persistence;

import com.xroig.finance.PostgresTestBase;
import com.xroig.finance.categories.domain.CategoryId;
import com.xroig.finance.categorization.domain.TransactionRecategorizer.RecategorizationCandidate;
import com.xroig.finance.accounts.infrastructure.persistence.AccountJpaEntity;
import com.xroig.finance.categories.infrastructure.persistence.CategoryJpaEntity;
import com.xroig.finance.transactions.infrastructure.persistence.TransactionJpaEntity;
import com.xroig.finance.accounts.infrastructure.persistence.AccountJpaRepository;
import com.xroig.finance.categories.infrastructure.persistence.CategoryJpaRepository;
import com.xroig.finance.transactions.infrastructure.persistence.TransactionJpaRepository;
import com.xroig.finance.shared.domain.TransactionType;
import com.xroig.finance.transactions.domain.TransactionId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Adapter test (Level 2) for the {@link TransactionRecategorizerAdapter} (the ACL over the
 * transactions store): lists the movements in a fallback category and moves the selected
 * ones to the target category, leaving the rest untouched.
 */
@Import(TransactionRecategorizerAdapter.class)
class TransactionRecategorizerAdapterTest extends PostgresTestBase {

    private static final LocalDate D = LocalDate.of(2024, 1, 10);

    @Autowired private TransactionRecategorizerAdapter adapter;
    @Autowired private AccountJpaRepository accountRepository;
    @Autowired private CategoryJpaRepository categoryRepository;
    @Autowired private TransactionJpaRepository transactionRepository;

    @Test
    void candidatesIn_listsMovementsWithDescriptionAndAccount() {
        AccountJpaEntity account = account("Corriente");
        CategoryJpaEntity fallback = category("Otros gastos", null);
        TransactionJpaEntity t = expense(account, fallback, "Compra MERCADONA");

        List<RecategorizationCandidate> candidates = adapter.candidatesIn(new CategoryId(fallback.getId()));

        assertThat(candidates).singleElement().satisfies(c -> {
            assertThat(c.id()).isEqualTo(new TransactionId(t.getId()));
            assertThat(c.description()).isEqualTo("Compra MERCADONA");
            assertThat(c.accountId().value()).isEqualTo(account.getId());
        });
    }

    @Test
    void reassign_movesOnlyTheSelectedMovements() {
        AccountJpaEntity account = account("Corriente");
        CategoryJpaEntity fallback = category("Otros gastos", null);
        CategoryJpaEntity target = category("Supermercado", null);
        TransactionJpaEntity moved = expense(account, fallback, "MERCADONA");
        TransactionJpaEntity kept = expense(account, fallback, "Farmacia");

        adapter.reassign(List.of(new TransactionId(moved.getId())), new CategoryId(target.getId()));

        // Read back through the adapter's own query (same session/entity, auto-flushed): the
        // moved one left the fallback for the target, the other stayed put.
        assertThat(adapter.candidatesIn(new CategoryId(fallback.getId())))
                .extracting(RecategorizationCandidate::id)
                .containsExactly(new TransactionId(kept.getId()));
        assertThat(adapter.candidatesIn(new CategoryId(target.getId())))
                .extracting(RecategorizationCandidate::id)
                .containsExactly(new TransactionId(moved.getId()));
    }

    @Test
    void reassign_emptySelection_isNoOp() {
        adapter.reassign(List.of(), new CategoryId(1L));
        assertThat(transactionRepository.count()).isZero();
    }

    private AccountJpaEntity account(String name) {
        AccountJpaEntity a = new AccountJpaEntity();
        a.setName(name);
        a.setType("Banco");
        a.setInitialBalance(BigDecimal.ZERO);
        return accountRepository.save(a);
    }

    private CategoryJpaEntity category(String name, AccountJpaEntity account) {
        CategoryJpaEntity c = new CategoryJpaEntity();
        c.setName(name);
        c.setType(TransactionType.EXPENSE);
        c.setColor("#000000");
        c.setAccount(account);
        return categoryRepository.save(c);
    }

    private TransactionJpaEntity expense(AccountJpaEntity account, CategoryJpaEntity category, String description) {
        TransactionJpaEntity t = new TransactionJpaEntity();
        t.setType(TransactionType.EXPENSE);
        t.setAmount(new BigDecimal("10"));
        t.setAccount(account);
        t.setCategory(category);
        t.setDate(D);
        t.setDescription(description);
        return transactionRepository.save(t);
    }
}
