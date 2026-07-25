package com.xroig.finance.categorization.domain;

import com.xroig.finance.accounts.domain.AccountId;
import com.xroig.finance.categories.domain.CategoryId;
import com.xroig.finance.shared.domain.TransactionType;

import java.util.Optional;

/**
 * Outbound port to look up the categories a rule reasons about, referencing the
 * categories context only by identity: the rule's <b>target</b> category (its type and
 * account scope) and the <b>fallback</b> category ("Otros gastos"/"Otros ingresos") of a
 * given type, from which recategorization pulls movements.
 */
public interface RuleCategoryCatalog {

    /** The target category's facts, or empty when it does not exist (→ invalid rule). */
    Optional<RuleCategory> find(CategoryId id);

    /** The fallback category ("Otros gastos"/"Otros ingresos") for a type, if it exists. */
    Optional<CategoryId> fallbackFor(TransactionType type);

    /** The minimal facts the categorization context needs about a category: id, type and owning account (null ⇒ global). */
    record RuleCategory(CategoryId id, TransactionType type, AccountId accountId) {

        public boolean isGlobal() {
            return accountId == null;
        }
    }
}
