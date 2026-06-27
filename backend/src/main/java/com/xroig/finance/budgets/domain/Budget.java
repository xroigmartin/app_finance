package com.xroig.finance.budgets.domain;

import com.xroig.finance.accounts.domain.AccountId;
import com.xroig.finance.categories.domain.CategoryId;
import com.xroig.finance.shared.domain.Money;
import com.xroig.finance.shared.domain.ValidationException;

import java.util.Objects;

/**
 * Budget aggregate root: the planned amount for a (leaf) category on a concrete
 * account in one calendar month. Pure of any framework or persistence concern;
 * references the account and the category by identity ({@link AccountId} /
 * {@link CategoryId}).
 *
 * <p>Invariants kept here: the month is 1..12 and the amount is positive. The
 * cross-aggregate guards (the category must exist, be a leaf and either be global
 * or belong to the same account, and the slot must be free) live in the
 * application service, which resolves them through outbound ports.
 */
public class Budget {

    private final BudgetId id;
    private AccountId accountId;
    private CategoryId categoryId;
    private int year;
    private int month;
    private Money amount;

    private Budget(BudgetId id, AccountId accountId, CategoryId categoryId, int year, int month, Money amount) {
        this.id = id;
        this.accountId = Objects.requireNonNull(accountId, "accountId");
        this.categoryId = Objects.requireNonNull(categoryId, "categoryId");
        this.year = year;
        this.month = requireValidMonth(month);
        this.amount = requirePositive(amount);
    }

    /** Factory for a new budget. */
    public static Budget create(AccountId accountId, CategoryId categoryId, int year, int month, Money amount) {
        return new Budget(null, accountId, categoryId, year, month, amount);
    }

    /** Rebuilds a persisted budget (identity present), from the persistence adapter. */
    public static Budget rehydrate(BudgetId id, AccountId accountId, CategoryId categoryId,
                                   int year, int month, Money amount) {
        if (id == null) {
            throw new IllegalArgumentException("Un presupuesto rehidratado necesita identidad");
        }
        return new Budget(id, accountId, categoryId, year, month, amount);
    }

    /** Re-applies the editable fields and slot, re-checking the invariants. */
    public void reassign(AccountId accountId, CategoryId categoryId, int year, int month, Money amount) {
        this.accountId = Objects.requireNonNull(accountId, "accountId");
        this.categoryId = Objects.requireNonNull(categoryId, "categoryId");
        this.year = year;
        this.month = requireValidMonth(month);
        this.amount = requirePositive(amount);
    }

    /** A brand-new budget (no identity) for the same account/category/amount placed in another slot. */
    public Budget copyTo(int year, int month) {
        return create(accountId, categoryId, year, month, amount);
    }

    /** True when this budget sits on the given account/category/year/month slot. */
    public boolean isAt(AccountId accountId, CategoryId categoryId, int year, int month) {
        return this.accountId.equals(accountId) && this.categoryId.equals(categoryId)
                && this.year == year && this.month == month;
    }

    public BudgetId id() {
        return id;
    }

    public AccountId accountId() {
        return accountId;
    }

    public CategoryId categoryId() {
        return categoryId;
    }

    public int year() {
        return year;
    }

    public int month() {
        return month;
    }

    public Money amount() {
        return amount;
    }

    private static int requireValidMonth(int month) {
        if (month < 1 || month > 12) {
            throw new ValidationException("El mes del presupuesto debe estar entre 1 y 12");
        }
        return month;
    }

    private static Money requirePositive(Money amount) {
        if (amount == null || !amount.isPositive()) {
            throw new ValidationException("El importe del presupuesto debe ser positivo");
        }
        return amount;
    }
}
