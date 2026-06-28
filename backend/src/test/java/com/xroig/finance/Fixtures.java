package com.xroig.finance;

import com.xroig.finance.accounts.infrastructure.persistence.AccountJpaEntity;
import com.xroig.finance.budgets.infrastructure.persistence.BudgetJpaEntity;
import com.xroig.finance.categories.infrastructure.persistence.CategoryJpaEntity;
import com.xroig.finance.shared.domain.TransactionType;

import java.math.BigDecimal;

/** Builders to keep test setup short and readable. */
public final class Fixtures {

    private Fixtures() {
    }

    public static BigDecimal eur(String value) {
        return new BigDecimal(value);
    }

    public static AccountJpaEntity account(long id, String name) {
        AccountJpaEntity a = new AccountJpaEntity();
        a.setId(id);
        a.setName(name);
        a.setType("CORRIENTE");
        a.setInitialBalance(BigDecimal.ZERO);
        return a;
    }

    public static AccountJpaEntity account(long id, String name, BigDecimal initialBalance) {
        AccountJpaEntity a = account(id, name);
        a.setInitialBalance(initialBalance);
        return a;
    }

    public static CategoryJpaEntity category(long id, String name, TransactionType type) {
        CategoryJpaEntity c = new CategoryJpaEntity();
        c.setId(id);
        c.setName(name);
        c.setType(type);
        c.setColor("#000000");
        return c;
    }

    public static CategoryJpaEntity category(long id, String name, TransactionType type, AccountJpaEntity account) {
        CategoryJpaEntity c = category(id, name, type);
        c.setAccount(account);
        return c;
    }

    public static BudgetJpaEntity budget(Long id, AccountJpaEntity account, CategoryJpaEntity category,
                                         int year, int month, BigDecimal amount) {
        BudgetJpaEntity b = new BudgetJpaEntity();
        b.setId(id);
        b.setAccount(account);
        b.setCategory(category);
        b.setYear(year);
        b.setMonth(month);
        b.setAmount(amount);
        return b;
    }
}
