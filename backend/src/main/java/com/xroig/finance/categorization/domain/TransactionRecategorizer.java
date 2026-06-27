package com.xroig.finance.categorization.domain;

import com.xroig.finance.accounts.domain.AccountId;
import com.xroig.finance.categories.domain.CategoryId;
import com.xroig.finance.transactions.domain.TransactionId;

import java.util.Collection;
import java.util.List;

/**
 * Outbound port over the transactions context (an anti-corruption boundary): lists the
 * fallback movements a rule could reclassify and moves the matching ones to the rule's
 * category. Only fallback movements are ever touched, so a category assigned explicitly
 * or by another rule is never overwritten.
 */
public interface TransactionRecategorizer {

    /** Movements currently sitting in the given fallback category. */
    List<RecategorizationCandidate> candidatesIn(CategoryId fallbackCategory);

    /** Moves the given movements to the target category. */
    void reassign(Collection<TransactionId> transactionIds, CategoryId target);

    /** The minimal facts needed to decide whether a movement matches a rule. */
    record RecategorizationCandidate(TransactionId id, String description, AccountId accountId) {
    }
}
