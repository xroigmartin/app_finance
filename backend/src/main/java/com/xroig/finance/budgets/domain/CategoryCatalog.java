package com.xroig.finance.budgets.domain;

import com.xroig.finance.accounts.domain.AccountId;
import com.xroig.finance.categories.domain.CategoryId;

import java.util.Optional;

/**
 * Outbound port to look a category up when budgeting it: it must exist, be a leaf
 * (a category with subcategories is the read-only aggregate of its children and
 * cannot be budgeted directly) and, if it is account-bound, match the budget's
 * account. The categories context is referenced only by identity.
 */
public interface CategoryCatalog {

    Optional<CategoryBudgetInfo> find(CategoryId id);

    /** The minimal facts the budgets context needs about a category: its id, owning account (null ⇒ global) and whether it has subcategories. */
    record CategoryBudgetInfo(CategoryId id, AccountId accountId, boolean hasChildren) {

        public boolean isGlobal() {
            return accountId == null;
        }
    }
}
