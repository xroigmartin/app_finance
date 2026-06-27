package com.xroig.finance.imports.domain;

import com.xroig.finance.shared.domain.TransactionType;

import java.util.List;

/**
 * Outbound port: the auto-categorization rules the import applies to rows without an
 * explicit category column. Each rule carries its target category's type and scope so
 * the use case can filter by the row's type/account before matching the pattern.
 * Bridged to the categorization context.
 */
public interface RuleDirectory {

    List<ImportRule> all();

    /** A rule as the import sees it: the pattern and the target category's id/type/scope. */
    record ImportRule(String pattern, long categoryId, TransactionType categoryType, Long categoryAccountId) {
    }
}
