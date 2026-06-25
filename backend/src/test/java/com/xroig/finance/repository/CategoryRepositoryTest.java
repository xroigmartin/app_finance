package com.xroig.finance.repository;

import com.xroig.finance.PostgresTestBase;
import com.xroig.finance.model.Account;
import com.xroig.finance.model.Category;
import com.xroig.finance.shared.domain.TransactionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Level-2 tests for {@link CategoryRepository} against real PostgreSQL (stage T4,
 * repository query coverage): {@code findVisibleForAccount} — the global ({@code
 * account is null}) plus account-owned union the category pickers rely on — and
 * the {@code findByParentId} children lookup.
 */
class CategoryRepositoryTest extends PostgresTestBase {

    @Autowired private CategoryRepository categoryRepository;
    @Autowired private AccountRepository accountRepository;

    private Account corriente;
    private Account ahorro;

    @BeforeEach
    void setUp() {
        corriente = account("Corriente");
        ahorro = account("Ahorro");
    }

    @Test
    void findVisibleForAccount_returnsGlobalsPlusOwnedButNotOtherAccounts() {
        Category global = category("Suministros", null, null);
        Category propia = category("Comida", corriente, null);
        category("Ajena", ahorro, null); // belongs to another account → excluded

        List<Category> visible = categoryRepository.findVisibleForAccount(corriente.getId());

        assertThat(visible).extracting(Category::getName)
                .containsExactlyInAnyOrder(global.getName(), propia.getName());
    }

    @Test
    void findByParentId_returnsOnlyDirectChildren() {
        Category hogar = category("Hogar", corriente, null);
        Category luz = category("Luz", corriente, hogar);
        Category agua = category("Agua", corriente, hogar);
        category("Comida", corriente, null); // not a child of hogar

        List<Category> children = categoryRepository.findByParentId(hogar.getId());

        assertThat(children).extracting(Category::getName)
                .containsExactlyInAnyOrder(luz.getName(), agua.getName());
    }

    // ---- helpers ----

    private Account account(String name) {
        Account a = new Account();
        a.setName(name);
        a.setType("CORRIENTE");
        a.setInitialBalance(BigDecimal.ZERO);
        return accountRepository.save(a);
    }

    private Category category(String name, Account account, Category parent) {
        Category c = new Category();
        c.setName(name);
        c.setType(TransactionType.EXPENSE);
        c.setColor("#" + name.toLowerCase());
        c.setAccount(account);
        c.setParent(parent);
        return categoryRepository.save(c);
    }
}
