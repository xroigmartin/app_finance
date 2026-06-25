package com.xroig.finance.accounts.domain;

import com.xroig.finance.shared.domain.Money;
import com.xroig.finance.shared.domain.ValidationException;

/**
 * Account aggregate root, pure of any framework or persistence concern.
 *
 * <p>Holds its own invariants (name and type are mandatory; the initial balance is
 * always present), so it can never be constructed or mutated into an invalid
 * state. The running balance is <b>not</b> a field: it is a computed concept —
 * {@code saldo = saldo inicial + neto de movimientos} — expressed by
 * {@link #balanceWith(Money)}; movements live in another aggregate and are netted
 * in by the application/read side, never stored here.
 *
 * <p>The account {@code type} is a free-form string (e.g. «Banco», «CORRIENTE»,
 * «AHORRO») exactly as the database stores it — there is no closed enum, so any
 * non-blank value is accepted, preserving the legacy behaviour.
 */
public class Account {

    private final AccountId id;
    private String name;
    private String type;
    private Money initialBalance;

    private Account(AccountId id, String name, String type, Money initialBalance) {
        this.id = id;
        this.name = requireText(name, "El nombre de la cuenta es obligatorio");
        this.type = requireText(type, "El tipo de cuenta es obligatorio");
        this.initialBalance = requireBalance(initialBalance);
    }

    /** Factory for a brand-new account that has no identity yet (the store assigns it). */
    public static Account create(String name, String type, Money initialBalance) {
        return new Account(null, name, type, initialBalance);
    }

    /** Rebuilds an account already persisted (identity present), from the persistence adapter. */
    public static Account rehydrate(AccountId id, String name, String type, Money initialBalance) {
        if (id == null) {
            throw new IllegalArgumentException("Una cuenta rehidratada necesita identidad");
        }
        return new Account(id, name, type, initialBalance);
    }

    /** Applies the editable fields at once, mirroring the PUT semantics, re-checking invariants. */
    public void update(String name, String type, Money initialBalance) {
        this.name = requireText(name, "El nombre de la cuenta es obligatorio");
        this.type = requireText(type, "El tipo de cuenta es obligatorio");
        this.initialBalance = requireBalance(initialBalance);
    }

    /**
     * The account's balance as the domain defines it: its initial balance plus the
     * net of its movements (income positive, expense/refund netted by the caller).
     * Never stored — always derived.
     */
    public Money balanceWith(Money netMovements) {
        return initialBalance.add(netMovements);
    }

    public AccountId id() {
        return id;
    }

    public String name() {
        return name;
    }

    public String type() {
        return type;
    }

    public Money initialBalance() {
        return initialBalance;
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new ValidationException(message);
        }
        return value;
    }

    private static Money requireBalance(Money value) {
        if (value == null) {
            throw new ValidationException("El saldo inicial de la cuenta es obligatorio");
        }
        return value;
    }
}
