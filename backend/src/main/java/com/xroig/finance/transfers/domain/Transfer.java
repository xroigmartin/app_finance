package com.xroig.finance.transfers.domain;

import com.xroig.finance.accounts.domain.AccountId;
import com.xroig.finance.shared.domain.Money;
import com.xroig.finance.shared.domain.ValidationException;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Transfer aggregate root: a movement of money between two of the user's accounts.
 * Pure of any framework or persistence concern; references both ends by identity
 * ({@link AccountId}).
 *
 * <p>Invariant kept here: the source and destination accounts must differ (and the
 * amount is positive, the date present). Whether each account exists is a
 * cross-aggregate check resolved by the application service. A transfer affects
 * balances (out of the source, into the destination) but is excluded from
 * income/expense aggregates — that behaviour lives in the reporting reads.
 */
public class Transfer {

    private final TransferId id;
    private LocalDate date;
    private Money amount;
    private String description;
    private AccountId fromAccountId;
    private AccountId toAccountId;

    private Transfer(TransferId id, LocalDate date, Money amount, String description,
                     AccountId fromAccountId, AccountId toAccountId) {
        this.id = id;
        this.date = requireDate(date);
        this.amount = requirePositive(amount);
        this.description = description;
        this.fromAccountId = Objects.requireNonNull(fromAccountId, "fromAccountId");
        this.toAccountId = Objects.requireNonNull(toAccountId, "toAccountId");
        requireDistinctEnds(fromAccountId, toAccountId);
    }

    /** Factory for a new transfer. */
    public static Transfer create(LocalDate date, Money amount, String description,
                                  AccountId fromAccountId, AccountId toAccountId) {
        return new Transfer(null, date, amount, description, fromAccountId, toAccountId);
    }

    /** Rebuilds a persisted transfer (identity present), from the persistence adapter. */
    public static Transfer rehydrate(TransferId id, LocalDate date, Money amount, String description,
                                     AccountId fromAccountId, AccountId toAccountId) {
        if (id == null) {
            throw new IllegalArgumentException("Una transferencia rehidratada necesita identidad");
        }
        return new Transfer(id, date, amount, description, fromAccountId, toAccountId);
    }

    /** Re-applies the editable fields and ends, re-checking the invariants. */
    public void reassign(LocalDate date, Money amount, String description,
                         AccountId fromAccountId, AccountId toAccountId) {
        requireDistinctEnds(fromAccountId, toAccountId);
        this.date = requireDate(date);
        this.amount = requirePositive(amount);
        this.description = description;
        this.fromAccountId = Objects.requireNonNull(fromAccountId, "fromAccountId");
        this.toAccountId = Objects.requireNonNull(toAccountId, "toAccountId");
    }

    public TransferId id() {
        return id;
    }

    public LocalDate date() {
        return date;
    }

    public Money amount() {
        return amount;
    }

    public String description() {
        return description;
    }

    public AccountId fromAccountId() {
        return fromAccountId;
    }

    public AccountId toAccountId() {
        return toAccountId;
    }

    private static void requireDistinctEnds(AccountId from, AccountId to) {
        if (Objects.equals(from, to)) {
            throw new ValidationException("La cuenta de origen y la de destino deben ser distintas");
        }
    }

    private static LocalDate requireDate(LocalDate date) {
        if (date == null) {
            throw new ValidationException("La fecha de la transferencia es obligatoria");
        }
        return date;
    }

    private static Money requirePositive(Money amount) {
        if (amount == null || !amount.isPositive()) {
            throw new ValidationException("El importe de la transferencia debe ser positivo");
        }
        return amount;
    }
}
