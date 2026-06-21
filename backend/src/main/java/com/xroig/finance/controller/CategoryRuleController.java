package com.xroig.finance.controller;

import com.xroig.finance.model.Category;
import com.xroig.finance.model.CategoryRule;
import com.xroig.finance.repository.CategoryRepository;
import com.xroig.finance.repository.CategoryRuleRepository;
import com.xroig.finance.service.RecategorizationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/category-rules")
public class CategoryRuleController {

    public record RuleRequest(@NotBlank String pattern, @NotNull Long categoryId) {
    }

    /** {@code recategorized}: movimientos de "Otros gastos/ingresos" movidos a la categoría de la regla. */
    public record RuleResponse(CategoryRule rule, int recategorized) {
    }

    private final CategoryRuleRepository ruleRepository;
    private final CategoryRepository categoryRepository;
    private final RecategorizationService recategorizationService;

    public CategoryRuleController(CategoryRuleRepository ruleRepository,
                                  CategoryRepository categoryRepository,
                                  RecategorizationService recategorizationService) {
        this.ruleRepository = ruleRepository;
        this.categoryRepository = categoryRepository;
        this.recategorizationService = recategorizationService;
    }

    @GetMapping
    public List<CategoryRule> findAll() {
        return ruleRepository.findAll();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RuleResponse create(@Valid @RequestBody RuleRequest request) {
        CategoryRule rule = new CategoryRule();
        apply(rule, request);
        rule = ruleRepository.save(rule);
        return new RuleResponse(rule, recategorizationService.applyRule(rule));
    }

    @PutMapping("/{id}")
    public RuleResponse update(@PathVariable Long id, @Valid @RequestBody RuleRequest request) {
        CategoryRule rule = ruleRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Regla no encontrada"));
        apply(rule, request);
        rule = ruleRepository.save(rule);
        return new RuleResponse(rule, recategorizationService.applyRule(rule));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        ruleRepository.deleteById(id);
    }

    private void apply(CategoryRule rule, RuleRequest request) {
        Category category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Categoría no válida"));
        rule.setPattern(request.pattern().trim());
        rule.setCategory(category);
    }
}
