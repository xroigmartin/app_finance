package com.xroig.finance.categories.infrastructure.persistence;

import com.xroig.finance.accounts.domain.AccountId;
import com.xroig.finance.categories.domain.CategoryId;
import com.xroig.finance.categories.domain.CategoryReferences;
import com.xroig.finance.budgets.infrastructure.persistence.RecurringBudgetJpaRepository;
import com.xroig.finance.repository.BudgetRepository;
import com.xroig.finance.repository.CategoryRuleRepository;
import com.xroig.finance.repository.TransactionRepository;
import org.springframework.stereotype.Component;

/**
 * Adapter for {@link CategoryReferences}: resolves the cross-aggregate guards against
 * the legacy movement/budget/rule stores plus the migrated budgets context's recurrence
 * store. When the remaining contexts migrate it will point at their own ports instead.
 */
@Component
public class CategoryReferencesAdapter implements CategoryReferences {

    private final TransactionRepository transactions;
    private final BudgetRepository budgets;
    private final CategoryRuleRepository rules;
    private final RecurringBudgetJpaRepository recurrences;

    public CategoryReferencesAdapter(TransactionRepository transactions, BudgetRepository budgets,
                                     CategoryRuleRepository rules, RecurringBudgetJpaRepository recurrences) {
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
