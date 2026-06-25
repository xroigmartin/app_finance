package com.xroig.finance.categories.application.port;

/** Inbound port: delete a category, guarded against subcategories/movements/budgets/rules. */
public interface DeleteCategory {

    void delete(long id);
}
