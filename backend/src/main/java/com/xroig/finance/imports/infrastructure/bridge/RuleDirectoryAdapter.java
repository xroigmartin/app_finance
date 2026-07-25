package com.xroig.finance.imports.infrastructure.bridge;

import com.xroig.finance.categories.application.CategoryView;
import com.xroig.finance.categorization.application.CategoryRuleQueryPort;
import com.xroig.finance.imports.domain.RuleDirectory;
import org.springframework.stereotype.Component;

import java.util.List;

/** Bridges {@link RuleDirectory} to the categorization context's read-side {@link CategoryRuleQueryPort}. */
@Component
class RuleDirectoryAdapter implements RuleDirectory {

    private final CategoryRuleQueryPort queries;

    RuleDirectoryAdapter(CategoryRuleQueryPort queries) {
        this.queries = queries;
    }

    @Override
    public List<ImportRule> all() {
        return queries.findAll().stream().map(view -> {
            CategoryView category = view.category();
            Long accountId = category.account() == null ? null : category.account().id();
            return new ImportRule(view.pattern(), category.id(), category.type(), accountId);
        }).toList();
    }
}
