package com.xroig.finance.categories.application.port;

import com.xroig.finance.categories.application.CategoryView;
import com.xroig.finance.model.TransactionType;

/** Inbound port: create a top-level category or a subcategory. */
public interface CreateCategory {

    CategoryView create(CreateCategoryCommand command);

    /**
     * Intent to create a category. {@code parentId} non-null requests a subcategory
     * (its type is inherited, so the {@code type} here is ignored); {@code accountId}
     * non-null requests an account-bound scope.
     */
    record CreateCategoryCommand(String name, TransactionType type, String color,
                                 Long parentId, Long accountId) {
    }
}
