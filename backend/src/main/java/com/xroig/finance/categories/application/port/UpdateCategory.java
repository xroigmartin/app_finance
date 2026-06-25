package com.xroig.finance.categories.application.port;

import com.xroig.finance.categories.application.CategoryView;
import com.xroig.finance.shared.domain.TransactionType;

/** Inbound port: edit a category, possibly changing its scope or its parent. */
public interface UpdateCategory {

    CategoryView update(long id, UpdateCategoryCommand command);

    /** Intent to change a category's editable fields, scope and/or parent. */
    record UpdateCategoryCommand(String name, TransactionType type, String color,
                                 Long parentId, Long accountId) {
    }
}
