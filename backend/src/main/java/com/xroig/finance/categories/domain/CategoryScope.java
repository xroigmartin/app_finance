package com.xroig.finance.categories.domain;

import com.xroig.finance.accounts.domain.AccountId;

import java.util.Objects;
import java.util.Optional;

/**
 * Value object for a category's scope: either <b>global</b> (common to every
 * account) or <b>bound</b> to a single account. Replaces the nullable
 * {@code account} reference with an explicit concept of the language, and
 * references the account by identity ({@link AccountId}), never by object.
 */
public final class CategoryScope {

    private static final CategoryScope GLOBAL = new CategoryScope(null);

    private final AccountId accountId; // null ⇒ global

    private CategoryScope(AccountId accountId) {
        this.accountId = accountId;
    }

    public static CategoryScope global() {
        return GLOBAL;
    }

    public static CategoryScope boundTo(AccountId accountId) {
        return new CategoryScope(Objects.requireNonNull(accountId, "accountId"));
    }

    public boolean isGlobal() {
        return accountId == null;
    }

    public boolean isBound() {
        return accountId != null;
    }

    public Optional<AccountId> accountId() {
        return Optional.ofNullable(accountId);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        return o instanceof CategoryScope other && Objects.equals(accountId, other.accountId);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(accountId);
    }

    @Override
    public String toString() {
        return isGlobal() ? "CategoryScope[global]" : "CategoryScope[account=" + accountId.value() + "]";
    }
}
