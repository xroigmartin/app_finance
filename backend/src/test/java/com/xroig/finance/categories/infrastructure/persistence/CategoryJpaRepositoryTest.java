package com.xroig.finance.categories.infrastructure.persistence;

import com.xroig.finance.PostgresTestBase;
import com.xroig.finance.accounts.infrastructure.persistence.AccountJpaEntity;
import com.xroig.finance.accounts.infrastructure.persistence.AccountJpaRepository;
import com.xroig.finance.shared.domain.TransactionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Level-2 tests for the query methods on {@link CategoryJpaRepository} against real
 * PostgreSQL: {@code findVisibleForAccount} — the global ({@code account is null}) plus
 * account-owned union the category pickers rely on — and the {@code findByParentId}
 * children lookup.
 */
class CategoryJpaRepositoryTest extends PostgresTestBase {

    @Autowired private CategoryJpaRepository categoryRepository;
    @Autowired private AccountJpaRepository accountRepository;

    private AccountJpaEntity corriente;
    private AccountJpaEntity ahorro;

    @BeforeEach
    void setUp() {
        corriente = account("Corriente");
        ahorro = account("Ahorro");
    }

    @Test
    void findVisibleForAccount_returnsGlobalsPlusOwnedButNotOtherAccounts() {
        CategoryJpaEntity global = category("Suministros", null, null);
        CategoryJpaEntity propia = category("Comida", corriente, null);
        category("Ajena", ahorro, null); // belongs to another account → excluded

        List<CategoryJpaEntity> visible = categoryRepository.findVisibleForAccount(corriente.getId());

        assertThat(visible).extracting(CategoryJpaEntity::getName)
                .containsExactlyInAnyOrder(global.getName(), propia.getName());
    }

    @Test
    void findByParentId_returnsOnlyDirectChildren() {
        CategoryJpaEntity hogar = category("Hogar", corriente, null);
        CategoryJpaEntity luz = category("Luz", corriente, hogar);
        CategoryJpaEntity agua = category("Agua", corriente, hogar);
        category("Comida", corriente, null); // not a child of hogar

        List<CategoryJpaEntity> children = categoryRepository.findByParentId(hogar.getId());

        assertThat(children).extracting(CategoryJpaEntity::getName)
                .containsExactlyInAnyOrder(luz.getName(), agua.getName());
    }

    // ---- helpers ----

    private AccountJpaEntity account(String name) {
        AccountJpaEntity a = new AccountJpaEntity();
        a.setName(name);
        a.setType("CORRIENTE");
        a.setInitialBalance(BigDecimal.ZERO);
        return accountRepository.save(a);
    }

    private CategoryJpaEntity category(String name, AccountJpaEntity account, CategoryJpaEntity parent) {
        CategoryJpaEntity c = new CategoryJpaEntity();
        c.setName(name);
        c.setType(TransactionType.EXPENSE);
        c.setColor("#" + name.toLowerCase());
        c.setAccount(account);
        c.setParent(parent);
        return categoryRepository.save(c);
    }
}
