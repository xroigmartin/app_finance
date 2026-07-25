package com.xroig.finance.categorization.application;

import com.xroig.finance.accounts.domain.AccountId;
import com.xroig.finance.categories.domain.CategoryId;
import com.xroig.finance.categorization.application.port.CreateRule;
import com.xroig.finance.categorization.application.port.DeleteRule;
import com.xroig.finance.categorization.application.port.FindRules;
import com.xroig.finance.categorization.application.port.UpdateRule;
import com.xroig.finance.categorization.domain.CategoryRule;
import com.xroig.finance.categorization.domain.CategoryRuleId;
import com.xroig.finance.categorization.domain.CategoryRuleRepository;
import com.xroig.finance.categorization.domain.RuleCategoryCatalog;
import com.xroig.finance.categorization.domain.RuleCategoryCatalog.RuleCategory;
import com.xroig.finance.categorization.domain.TransactionRecategorizer;
import com.xroig.finance.categorization.domain.TransactionRecategorizer.RecategorizationCandidate;
import com.xroig.finance.shared.domain.NotFoundException;
import com.xroig.finance.shared.domain.ValidationException;
import com.xroig.finance.transactions.domain.TransactionId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Application service for the categorization context. Creating or editing a rule
 * validates its target category, persists the aggregate and then re-applies it to the
 * fallback movements, preserving the legacy order: target-category existence (→ 400) is
 * checked after the not-found (→ 404) on edit, and the recategorization runs only when a
 * fallback of the right type exists and differs from the rule's own category.
 */
@Service
@Transactional
public class CategoryRuleService implements FindRules, CreateRule, UpdateRule, DeleteRule {

    private final CategoryRuleRepository rules;
    private final RuleCategoryCatalog categories;
    private final TransactionRecategorizer transactions;
    private final CategoryRuleQueryPort queries;

    public CategoryRuleService(CategoryRuleRepository rules, RuleCategoryCatalog categories,
                               TransactionRecategorizer transactions, CategoryRuleQueryPort queries) {
        this.rules = rules;
        this.categories = categories;
        this.transactions = transactions;
        this.queries = queries;
    }

    @Override
    @Transactional(readOnly = true)
    public List<CategoryRuleView> findAll() {
        return queries.findAll();
    }

    @Override
    public RuleSaved create(RuleCommand command) {
        RuleCategory target = requireCategory(command.categoryId());
        CategoryRule saved = rules.save(CategoryRule.create(command.pattern(), target.id()));
        return new RuleSaved(view(saved.id()), applyRule(saved, target));
    }

    @Override
    public RuleSaved update(long id, RuleCommand command) {
        CategoryRule existing = rules.findById(new CategoryRuleId(id))
                .orElseThrow(() -> new NotFoundException("Regla no encontrada"));
        RuleCategory target = requireCategory(command.categoryId());
        existing.changeTo(command.pattern(), target.id());
        CategoryRule saved = rules.save(existing);
        return new RuleSaved(view(saved.id()), applyRule(saved, target));
    }

    @Override
    public void delete(long id) {
        rules.deleteById(new CategoryRuleId(id));
    }

    /**
     * Moves the fallback movements ("Otros gastos"/"Otros ingresos") matching the rule to
     * the rule's category. An account-bound target only takes movements of that account; a
     * global one takes them regardless. Returns how many were moved.
     */
    private int applyRule(CategoryRule rule, RuleCategory target) {
        Optional<CategoryId> fallback = categories.fallbackFor(target.type());
        if (fallback.isEmpty() || fallback.get().equals(rule.categoryId())) {
            return 0;
        }
        AccountId scope = target.accountId();
        List<TransactionId> toMove = transactions.candidatesIn(fallback.get()).stream()
                .filter(c -> scope == null || scope.equals(c.accountId()))
                .filter(c -> rule.matches(c.description()))
                .map(RecategorizationCandidate::id)
                .toList();
        transactions.reassign(toMove, rule.categoryId());
        return toMove.size();
    }

    private RuleCategory requireCategory(Long categoryId) {
        return categories.find(new CategoryId(categoryId))
                .orElseThrow(() -> new ValidationException("Categoría no válida"));
    }

    private CategoryRuleView view(CategoryRuleId id) {
        return queries.findById(id).orElseThrow(
                () -> new IllegalStateException("La regla recién guardada no se pudo leer: " + id.value()));
    }
}
