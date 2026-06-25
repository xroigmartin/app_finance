package com.xroig.finance.categories.infrastructure.persistence;

import com.xroig.finance.accounts.domain.AccountId;
import com.xroig.finance.categories.domain.CategoryId;
import com.xroig.finance.repository.BudgetRepository;
import com.xroig.finance.repository.CategoryRuleRepository;
import com.xroig.finance.repository.RecurringBudgetRepository;
import com.xroig.finance.repository.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/** Unit test for {@link CategoryReferencesAdapter}: each guard delegates to the right legacy store. */
@ExtendWith(MockitoExtension.class)
class CategoryReferencesAdapterTest {

    @Mock private TransactionRepository transactions;
    @Mock private BudgetRepository budgets;
    @Mock private CategoryRuleRepository rules;
    @Mock private RecurringBudgetRepository recurrences;
    @InjectMocks private CategoryReferencesAdapter adapter;

    @Test
    void hasTransactions_delegates() {
        when(transactions.existsByCategoryId(7L)).thenReturn(true);
        assertThat(adapter.hasTransactions(new CategoryId(7L))).isTrue();
    }

    @Test
    void hasTransactionsOnOtherAccount_delegates() {
        when(transactions.existsByCategoryIdAndAccountIdNot(7L, 1L)).thenReturn(true);
        assertThat(adapter.hasTransactionsOnOtherAccount(new CategoryId(7L), new AccountId(1L))).isTrue();
    }

    @Test
    void hasBudget_delegates() {
        when(budgets.existsByCategoryId(7L)).thenReturn(true);
        assertThat(adapter.hasBudget(new CategoryId(7L))).isTrue();
    }

    @Test
    void hasRule_delegates() {
        when(rules.existsByCategoryId(7L)).thenReturn(true);
        assertThat(adapter.hasRule(new CategoryId(7L))).isTrue();
    }

    @Test
    void hasRecurrence_delegates() {
        when(recurrences.existsByCategoryId(7L)).thenReturn(false);
        assertThat(adapter.hasRecurrence(new CategoryId(7L))).isFalse();
    }
}
