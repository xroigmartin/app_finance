package com.xroig.finance.transactions.domain;

import com.xroig.finance.accounts.domain.AccountId;
import com.xroig.finance.categories.domain.CategoryId;

import java.util.Optional;

/**
 * Outbound port to look a category up when assigning it to a movement: it must exist
 * and, if it is account-bound, must match the movement's account. The categories
 * context is referenced only by identity.
 */
public interface CategoryCatalog {

    Optional<CategoryDescriptor> find(CategoryId id);

    /** The minimal facts the transactions context needs about a category: its id and owning account (null ⇒ global). */
    record CategoryDescriptor(CategoryId id, AccountId accountId) {

        public boolean isGlobal() {
            return accountId == null;
        }
    }
}
