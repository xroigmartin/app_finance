package com.xroig.finance.transactions.domain;

import com.xroig.finance.accounts.domain.AccountId;
import com.xroig.finance.categories.domain.CategoryId;
import com.xroig.finance.shared.domain.Money;
import com.xroig.finance.shared.domain.TransactionType;
import com.xroig.finance.shared.domain.ValidationException;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Transaction (movement) aggregate root, pure of any framework or persistence
 * concern. References its account and category by identity
 * ({@link AccountId}/{@link CategoryId}) and, when it is a <b>refund</b>, the
 * refunded movement by {@link TransactionId}.
 *
 * <p>The refund semantics live here as an invariant: a refund (total or partial)
 * of an expense inherits the original's type, account and category; it cannot
 * refund an income, a refund, or exceed the original's pending amount. Whether the
 * original exists and is not the movement itself are resolved by the application
 * service (they need identity/lookup); everything else is enforced in
 * {@link #requireRefundable}.
 */
public class Transaction {

    private final TransactionId id;
    private LocalDate date;
    private Money amount;
    private String description;
    private TransactionType type;
    private AccountId accountId;
    private CategoryId categoryId;
    private TransactionId refundOfId; // null ⇒ a normal movement

    private Transaction(TransactionId id, LocalDate date, Money amount, String description,
                        TransactionType type, AccountId accountId, CategoryId categoryId,
                        TransactionId refundOfId) {
        this.id = id;
        this.date = requireDate(date);
        this.amount = requirePositive(amount);
        this.description = description;
        this.type = Objects.requireNonNull(type, "type");
        this.accountId = Objects.requireNonNull(accountId, "accountId");
        this.categoryId = Objects.requireNonNull(categoryId, "categoryId");
        this.refundOfId = refundOfId;
    }

    /** Factory for a new normal movement. */
    public static Transaction record(LocalDate date, Money amount, String description,
                                     TransactionType type, AccountId accountId, CategoryId categoryId) {
        return new Transaction(null, date, amount, description, type, accountId, categoryId, null);
    }

    /**
     * Factory for a new refund of {@code original}. Inherits the original's type,
     * account and category; {@code alreadyRefunded} is the sum already refunded of
     * that original (resolved by the caller).
     */
    public static Transaction refundOf(LocalDate date, Money amount, String description,
                                       Transaction original, Money alreadyRefunded) {
        requireRefundable(original, amount, alreadyRefunded);
        return new Transaction(null, date, amount, description,
                original.type, original.accountId, original.categoryId, original.id);
    }

    /** Rebuilds a persisted movement (identity present), from the persistence adapter. */
    public static Transaction rehydrate(TransactionId id, LocalDate date, Money amount, String description,
                                        TransactionType type, AccountId accountId, CategoryId categoryId,
                                        TransactionId refundOfId) {
        if (id == null) {
            throw new IllegalArgumentException("Un movimiento rehidratado necesita identidad");
        }
        return new Transaction(id, date, amount, description, type, accountId, categoryId, refundOfId);
    }

    /** Re-applies this movement as a normal one (clears any refund link). */
    public void changeToNormal(LocalDate date, Money amount, String description,
                               TransactionType type, AccountId accountId, CategoryId categoryId) {
        this.date = requireDate(date);
        this.amount = requirePositive(amount);
        this.description = description;
        this.type = Objects.requireNonNull(type, "type");
        this.accountId = Objects.requireNonNull(accountId, "accountId");
        this.categoryId = Objects.requireNonNull(categoryId, "categoryId");
        this.refundOfId = null;
    }

    /** Re-applies this movement as a refund of {@code original}, inheriting its type/account/category. */
    public void changeToRefund(LocalDate date, Money amount, String description,
                               Transaction original, Money alreadyRefunded) {
        requireRefundable(original, amount, alreadyRefunded);
        this.date = requireDate(date);
        this.amount = requirePositive(amount);
        this.description = description;
        this.type = original.type;
        this.accountId = original.accountId;
        this.categoryId = original.categoryId;
        this.refundOfId = original.id;
    }

    public boolean isExpense() {
        return type == TransactionType.EXPENSE;
    }

    public boolean isRefund() {
        return refundOfId != null;
    }

    public TransactionId id() {
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

    public TransactionType type() {
        return type;
    }

    public AccountId accountId() {
        return accountId;
    }

    public CategoryId categoryId() {
        return categoryId;
    }

    public TransactionId refundOfId() {
        return refundOfId;
    }

    private static void requireRefundable(Transaction original, Money amount, Money alreadyRefunded) {
        Objects.requireNonNull(original, "original");
        if (original.isRefund()) {
            throw new ValidationException("No se puede registrar una devolución de otra devolución");
        }
        if (!original.isExpense()) {
            throw new ValidationException("Solo se pueden registrar devoluciones de gastos");
        }
        Money pending = original.amount.subtract(alreadyRefunded);
        if (requirePositive(amount).compareTo(pending) > 0) {
            throw new ValidationException(
                    "La devolución supera el importe pendiente del gasto (pendiente: "
                            + pending.amount().toPlainString() + ")");
        }
    }

    private static LocalDate requireDate(LocalDate date) {
        if (date == null) {
            throw new ValidationException("La fecha del movimiento es obligatoria");
        }
        return date;
    }

    private static Money requirePositive(Money amount) {
        if (amount == null || !amount.isPositive()) {
            throw new ValidationException("El importe del movimiento debe ser positivo");
        }
        return amount;
    }
}
