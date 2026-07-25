package com.xroig.finance.categorization.infrastructure.persistence;

import com.xroig.finance.PostgresTestBase;
import com.xroig.finance.categories.domain.CategoryId;
import com.xroig.finance.categorization.domain.RuleCategoryCatalog.RuleCategory;
import com.xroig.finance.accounts.infrastructure.persistence.AccountJpaEntity;
import com.xroig.finance.categories.infrastructure.persistence.CategoryJpaEntity;
import com.xroig.finance.accounts.infrastructure.persistence.AccountJpaRepository;
import com.xroig.finance.categories.infrastructure.persistence.CategoryJpaRepository;
import com.xroig.finance.shared.domain.TransactionType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Adapter test (Level 2) for the {@link RuleCategoryCatalogAdapter}: it reports a target
 * category's type and account scope, and resolves the fallback category ("Otros
 * gastos"/"Otros ingresos") by type and name, as the legacy recategorization did.
 */
@Import(RuleCategoryCatalogAdapter.class)
class RuleCategoryCatalogAdapterTest extends PostgresTestBase {

    @Autowired private RuleCategoryCatalogAdapter adapter;
    @Autowired private AccountJpaRepository accountRepository;
    @Autowired private CategoryJpaRepository categoryRepository;

    @Test
    void find_accountBoundCategory_reportsTypeAndAccount() {
        AccountJpaEntity account = account("Corriente");
        CategoryJpaEntity cat = category("Supermercado", TransactionType.EXPENSE, account);

        assertThat(adapter.find(new CategoryId(cat.getId()))).hasValueSatisfying((RuleCategory rc) -> {
            assertThat(rc.type()).isEqualTo(TransactionType.EXPENSE);
            assertThat(rc.isGlobal()).isFalse();
            assertThat(rc.accountId().value()).isEqualTo(account.getId());
        });
    }

    @Test
    void find_globalCategory_reportsGlobal() {
        CategoryJpaEntity cat = category("Suministros", TransactionType.EXPENSE, null);

        assertThat(adapter.find(new CategoryId(cat.getId()))).hasValueSatisfying(rc -> {
            assertThat(rc.isGlobal()).isTrue();
            assertThat(rc.accountId()).isNull();
        });
    }

    @Test
    void find_unknownCategory_isEmpty() {
        assertThat(adapter.find(new CategoryId(-1L))).isEmpty();
    }

    @Test
    void fallbackFor_matchesByTypeAndName_caseInsensitive() {
        CategoryJpaEntity otrosGastos = category("Otros gastos", TransactionType.EXPENSE, null);
        CategoryJpaEntity otrosIngresos = category("Otros ingresos", TransactionType.INCOME, null);

        assertThat(adapter.fallbackFor(TransactionType.EXPENSE))
                .hasValue(new CategoryId(otrosGastos.getId()));
        assertThat(adapter.fallbackFor(TransactionType.INCOME))
                .hasValue(new CategoryId(otrosIngresos.getId()));
    }

    @Test
    void fallbackFor_missingFallback_isEmpty() {
        assertThat(adapter.fallbackFor(TransactionType.EXPENSE)).isEmpty();
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
}
