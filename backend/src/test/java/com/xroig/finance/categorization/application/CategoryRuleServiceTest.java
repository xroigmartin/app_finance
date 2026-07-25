package com.xroig.finance.categorization.application;

import com.xroig.finance.accounts.domain.AccountId;
import com.xroig.finance.categories.application.CategoryView;
import com.xroig.finance.categories.domain.CategoryId;
import com.xroig.finance.categorization.application.port.CreateRule.RuleCommand;
import com.xroig.finance.categorization.domain.CategoryRule;
import com.xroig.finance.categorization.domain.CategoryRuleId;
import com.xroig.finance.categorization.domain.CategoryRuleRepository;
import com.xroig.finance.categorization.domain.RuleCategoryCatalog;
import com.xroig.finance.categorization.domain.RuleCategoryCatalog.RuleCategory;
import com.xroig.finance.categorization.domain.TransactionRecategorizer;
import com.xroig.finance.categorization.domain.TransactionRecategorizer.RecategorizationCandidate;
import com.xroig.finance.shared.domain.NotFoundException;
import com.xroig.finance.shared.domain.TransactionType;
import com.xroig.finance.shared.domain.ValidationException;
import com.xroig.finance.transactions.domain.TransactionId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Application-service tests for the categorization context with the outbound ports mocked:
 * the create/update guard order (404 before 400), the recategorization side effect (fallback
 * resolution, account scoping, matching) and the delete/find delegations.
 */
@ExtendWith(MockitoExtension.class)
class CategoryRuleServiceTest {

    @Mock private CategoryRuleRepository rules;
    @Mock private RuleCategoryCatalog categories;
    @Mock private TransactionRecategorizer transactions;
    @Mock private CategoryRuleQueryPort queries;
    @InjectMocks private CategoryRuleService service;

    @Captor private ArgumentCaptor<Collection<TransactionId>> movedCaptor;

    private final CategoryId targetId = new CategoryId(10L);
    private final CategoryId fallbackId = new CategoryId(100L);
    private final AccountId corriente = new AccountId(1L);
    private final AccountId ahorro = new AccountId(2L);

    // ---------- find / delete ----------

    @Test
    void findAll_delegatesToQueries() {
        List<CategoryRuleView> views = List.of(view(1L, "lidl"));
        when(queries.findAll()).thenReturn(views);
        assertThat(service.findAll()).isEqualTo(views);
    }

    @Test
    void delete_delegatesToRepository() {
        service.delete(5L);
        verify(rules).deleteById(new CategoryRuleId(5L));
    }

    // ---------- create ----------

    @Test
    void create_unknownCategory_throwsValidation() {
        when(categories.find(targetId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(new RuleCommand("lidl", 10L)))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Categoría no válida");
        verify(rules, never()).save(any());
    }

    @Test
    void create_savesTrimsAndAppliesRule_globalTargetTakesAllAccounts() {
        RuleCategory global = new RuleCategory(targetId, TransactionType.EXPENSE, null);
        when(categories.find(targetId)).thenReturn(Optional.of(global));
        when(categories.fallbackFor(TransactionType.EXPENSE)).thenReturn(Optional.of(fallbackId));
        when(rules.save(any())).thenReturn(saved("lidl|mercadona"));
        when(transactions.candidatesIn(fallbackId)).thenReturn(List.of(
                candidate(1L, "Compra MERCADONA centro", corriente),
                candidate(2L, "Pago farmacia", corriente),
                candidate(3L, "LIDL", ahorro)));
        when(queries.findById(new CategoryRuleId(1L))).thenReturn(Optional.of(view(1L, "lidl|mercadona")));

        RuleSaved result = service.create(new RuleCommand("  lidl|mercadona  ", 10L));

        assertThat(result.recategorized()).isEqualTo(2);
        assertThat(result.rule().pattern()).isEqualTo("lidl|mercadona");
        verify(transactions).reassign(movedCaptor.capture(), eqTarget());
        assertThat(movedCaptor.getValue()).containsExactly(new TransactionId(1L), new TransactionId(3L));
    }

    @Test
    void create_accountBoundTarget_onlyTakesThatAccount() {
        RuleCategory bound = new RuleCategory(targetId, TransactionType.EXPENSE, corriente);
        when(categories.find(targetId)).thenReturn(Optional.of(bound));
        when(categories.fallbackFor(TransactionType.EXPENSE)).thenReturn(Optional.of(fallbackId));
        when(rules.save(any())).thenReturn(saved("mercadona"));
        when(transactions.candidatesIn(fallbackId)).thenReturn(List.of(
                candidate(1L, "MERCADONA", corriente),
                candidate(2L, "MERCADONA", ahorro)));
        when(queries.findById(new CategoryRuleId(1L))).thenReturn(Optional.of(view(1L, "mercadona")));

        RuleSaved result = service.create(new RuleCommand("mercadona", 10L));

        assertThat(result.recategorized()).isEqualTo(1);
        verify(transactions).reassign(movedCaptor.capture(), eqTarget());
        assertThat(movedCaptor.getValue()).containsExactly(new TransactionId(1L));
    }

    @Test
    void create_noFallbackCategory_recategorizesNothing() {
        RuleCategory global = new RuleCategory(targetId, TransactionType.EXPENSE, null);
        when(categories.find(targetId)).thenReturn(Optional.of(global));
        when(categories.fallbackFor(TransactionType.EXPENSE)).thenReturn(Optional.empty());
        when(rules.save(any())).thenReturn(saved("lidl"));
        when(queries.findById(new CategoryRuleId(1L))).thenReturn(Optional.of(view(1L, "lidl")));

        RuleSaved result = service.create(new RuleCommand("lidl", 10L));

        assertThat(result.recategorized()).isZero();
        verify(transactions, never()).candidatesIn(any());
        verify(transactions, never()).reassign(any(), any());
    }

    @Test
    void create_ruleTargetsTheFallbackItself_recategorizesNothing() {
        RuleCategory global = new RuleCategory(fallbackId, TransactionType.EXPENSE, null);
        when(categories.find(fallbackId)).thenReturn(Optional.of(global));
        when(categories.fallbackFor(TransactionType.EXPENSE)).thenReturn(Optional.of(fallbackId));
        when(rules.save(any())).thenReturn(
                CategoryRule.rehydrate(new CategoryRuleId(1L), "lidl", fallbackId));
        when(queries.findById(new CategoryRuleId(1L))).thenReturn(Optional.of(view(1L, "lidl")));

        RuleSaved result = service.create(new RuleCommand("lidl", 100L));

        assertThat(result.recategorized()).isZero();
        verify(transactions, never()).candidatesIn(any());
    }

    // ---------- update ----------

    @Test
    void update_notFound_throws404() {
        when(rules.findById(new CategoryRuleId(5L))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(5L, new RuleCommand("lidl", 10L)))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Regla no encontrada");
        verify(categories, never()).find(any());
    }

    @Test
    void update_unknownCategory_throwsValidation_after404Check() {
        when(rules.findById(new CategoryRuleId(5L)))
                .thenReturn(Optional.of(CategoryRule.rehydrate(new CategoryRuleId(5L), "viejo", targetId)));
        when(categories.find(targetId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(5L, new RuleCommand("lidl", 10L)))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Categoría no válida");
        verify(rules, never()).save(any());
    }

    @Test
    void update_appliesNewPatternAndCategory() {
        CategoryRule existing = CategoryRule.rehydrate(new CategoryRuleId(5L), "viejo", new CategoryId(20L));
        when(rules.findById(new CategoryRuleId(5L))).thenReturn(Optional.of(existing));
        RuleCategory global = new RuleCategory(targetId, TransactionType.INCOME, null);
        when(categories.find(targetId)).thenReturn(Optional.of(global));
        when(categories.fallbackFor(TransactionType.INCOME)).thenReturn(Optional.of(fallbackId));
        when(rules.save(existing)).thenReturn(existing);
        when(transactions.candidatesIn(fallbackId)).thenReturn(List.of(candidate(7L, "Nómina", corriente)));
        when(queries.findById(new CategoryRuleId(5L))).thenReturn(Optional.of(view(5L, "nomina")));

        RuleSaved result = service.update(5L, new RuleCommand("nomina", 10L));

        assertThat(existing.pattern()).isEqualTo("nomina");
        assertThat(existing.categoryId()).isEqualTo(targetId);
        assertThat(result.recategorized()).isEqualTo(1);
    }

    // ---------- helpers ----------

    private CategoryRule saved(String pattern) {
        return CategoryRule.rehydrate(new CategoryRuleId(1L), pattern, targetId);
    }

    private RecategorizationCandidate candidate(long id, String description, AccountId account) {
        return new RecategorizationCandidate(new TransactionId(id), description, account);
    }

    private CategoryRuleView view(long id, String pattern) {
        CategoryView category = new CategoryView(targetId.value(), "Supermercado",
                TransactionType.EXPENSE, "#000000", null, null);
        return new CategoryRuleView(id, pattern, category);
    }

    private CategoryId eqTarget() {
        return org.mockito.ArgumentMatchers.eq(targetId);
    }
}
