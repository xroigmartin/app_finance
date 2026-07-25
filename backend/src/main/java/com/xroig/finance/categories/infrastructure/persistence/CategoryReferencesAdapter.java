package com.xroig.finance.categories.infrastructure.persistence;

import com.xroig.finance.accounts.domain.AccountId;
import com.xroig.finance.categories.domain.CategoryId;
import com.xroig.finance.categories.domain.CategoryReferences;
import com.xroig.finance.budgets.infrastructure.persistence.BudgetJpaRepository;
import com.xroig.finance.budgets.infrastructure.persistence.RecurringBudgetJpaRepository;
import com.xroig.finance.categorization.infrastructure.persistence.CategoryRuleJpaRepository;
import com.xroig.finance.transactions.infrastructure.persistence.TransactionJpaRepository;
import org.springframework.stereotype.Component;

/**
 * Adapter for {@link CategoryReferences}: resolves the cross-aggregate guards against the
 * movements, budgets, recurrences and rules stores of their migrated contexts.
 */
@Component
public class CategoryReferencesAdapter implements CategoryReferences {

    private final TransactionJpaRepository transactions;
    private final BudgetJpaRepository budgets;
    private final CategoryRuleJpaRepository rules;
    private final RecurringBudgetJpaRepository recurrences;

    public CategoryReferencesAdapter(TransactionJpaRepository transactions, BudgetJpaRepository budgets,
                                     CategoryRuleJpaRepository rules, RecurringBudgetJpaRepository recurrences) {
        this.transactions = transactions;
        this.budgets = budgets;
        this.rules = rules;
        this.recurrences = recurrences;
    }

    @Override
    public boolean hasTransactions(CategoryId id) {
        return transactions.existsByCategoryId(id.value());
    }

    @Override
    public boolean hasTransactionsOnOtherAccount(CategoryId id, AccountId accountId) {
        return transactions.existsByCategoryIdAndAccountIdNot(id.value(), accountId.value());
    }

    @Override
    public boolean hasBudget(CategoryId id) {
        return budgets.existsByCategoryId(id.value());
    }

    @Override
    public boolean hasRule(CategoryId id) {
        return rules.existsByCategoryId(id.value());
    }

    @Override
    public boolean hasRecurrence(CategoryId id) {
        return recurrences.existsByCategoryId(id.value());
    }
}
