package com.xroig.finance.categorization.infrastructure.web;

import com.xroig.finance.categorization.application.CategoryRuleView;
import com.xroig.finance.categorization.application.RuleSaved;
import com.xroig.finance.categorization.application.port.CreateRule;
import com.xroig.finance.categorization.application.port.CreateRule.RuleCommand;
import com.xroig.finance.categorization.application.port.DeleteRule;
import com.xroig.finance.categorization.application.port.FindRules;
import com.xroig.finance.categorization.application.port.UpdateRule;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Inbound web adapter for the categorization context. Thin: it (de)serializes DTOs and
 * delegates to the inbound ports, returning the {@link CategoryRuleView} read model on
 * read and the {@link RuleSaved} result (rule + recategorized count) on write.
 */
@RestController
@RequestMapping("/api/category-rules")
public class CategoryRuleController {

    private final FindRules findRules;
    private final CreateRule createRule;
    private final UpdateRule updateRule;
    private final DeleteRule deleteRule;

    public CategoryRuleController(FindRules findRules, CreateRule createRule,
                                 UpdateRule updateRule, DeleteRule deleteRule) {
        this.findRules = findRules;
        this.createRule = createRule;
        this.updateRule = updateRule;
        this.deleteRule = deleteRule;
    }

    @GetMapping
    public List<CategoryRuleView> findAll() {
        return findRules.findAll();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RuleSaved create(@Valid @RequestBody CategoryRuleRequest request) {
        return createRule.create(toCommand(request));
    }

    @PutMapping("/{id}")
    public RuleSaved update(@PathVariable Long id, @Valid @RequestBody CategoryRuleRequest request) {
        return updateRule.update(id, toCommand(request));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        deleteRule.delete(id);
    }

    private static RuleCommand toCommand(CategoryRuleRequest r) {
        return new RuleCommand(r.pattern(), r.categoryId());
    }
}
