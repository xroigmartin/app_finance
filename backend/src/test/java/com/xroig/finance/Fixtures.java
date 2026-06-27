package com.xroig.finance;

import com.xroig.finance.model.Account;
import com.xroig.finance.model.Budget;
import com.xroig.finance.model.Category;
import com.xroig.finance.model.Transaction;
import com.xroig.finance.shared.domain.TransactionType;
import com.xroig.finance.model.Transfer;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Builders to keep test setup short and readable. */
public final class Fixtures {

    private Fixtures() {
    }

    public static BigDecimal eur(String value) {
        return new BigDecimal(value);
    }

    public static Account account(long id, String name) {
        Account a = new Account();
        a.setId(id);
        a.setName(name);
        a.setType("CORRIENTE");
        a.setInitialBalance(BigDecimal.ZERO);
        return a;
    }

    public static Account account(long id, String name, BigDecimal initialBalance) {
        Account a = account(id, name);
        a.setInitialBalance(initialBalance);
        return a;
    }

    public static Category category(long id, String name, TransactionType type) {
        Category c = new Category();
        c.setId(id);
        c.setName(name);
        c.setType(type);
        c.setColor("#000000");
        return c;
    }

    public static Category category(long id, String name, TransactionType type, Account account) {
        Category c = category(id, name, type);
        c.setAccount(account);
        return c;
    }

    /** Subcategory inheriting its parent's type and account scope. */
    public static Category subcategory(long id, String name, Category parent) {
        Category c = category(id, name, parent.getType());
        c.setParent(parent);
        c.setAccount(parent.getAccount());
        return c;
    }

    public static Transaction transaction(Long id, TransactionType type, BigDecimal amount,
                                          Account account, Category category, LocalDate date) {
        Transaction t = new Transaction();
        t.setId(id);
        t.setType(type);
        t.setAmount(amount);
        t.setAccount(account);
        t.setCategory(category);
        t.setDate(date);
        return t;
    }

    public static Transaction expense(Long id, BigDecimal amount, Account account,
                                      Category category, LocalDate date) {
        return transaction(id, TransactionType.EXPENSE, amount, account, category, date);
    }

    public static Transaction income(Long id, BigDecimal amount, Account account,
                                     Category category, LocalDate date) {
        return transaction(id, TransactionType.INCOME, amount, account, category, date);
    }

    public static Transfer transfer(Long id, BigDecimal amount, Account from, Account to, LocalDate date) {
        Transfer t = new Transfer();
        t.setId(id);
        t.setAmount(amount);
        t.setFromAccount(from);
        t.setToAccount(to);
        t.setDate(date);
        return t;
    }

    public static Budget budget(Long id, Account account, Category category,
                                int year, int month, BigDecimal amount) {
        Budget b = new Budget();
        b.setId(id);
        b.setAccount(account);
        b.setCategory(category);
        b.setYear(year);
        b.setMonth(month);
        b.setAmount(amount);
        return b;
    }
}
