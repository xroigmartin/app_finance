package com.xroig.finance.service;

import com.xroig.finance.model.Category;
import com.xroig.finance.model.CategoryRule;
import com.xroig.finance.model.Transaction;
import com.xroig.finance.shared.domain.TransactionType;
import com.xroig.finance.repository.CategoryRepository;
import com.xroig.finance.repository.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

/**
 * Re-applies a categorization rule to the transactions that fell back to
 * "Otros gastos" / "Otros ingresos", so creating or editing a rule does not
 * require recategorizing them by hand. Only fallback transactions are touched:
 * a category assigned explicitly or by another rule is never overwritten.
 */
@Service
public class RecategorizationService {

    private final TransactionRepository transactionRepository;
    private final CategoryRepository categoryRepository;

    public RecategorizationService(TransactionRepository transactionRepository,
                                   CategoryRepository categoryRepository) {
        this.transactionRepository = transactionRepository;
        this.categoryRepository = categoryRepository;
    }

    /**
     * Same matching as the import: any of the "|"-separated pattern
     * alternatives, compared without case or accents, contained in the
     * description.
     */
    public static boolean matches(CategoryRule rule, String description) {
        if (description == null) {
            return false;
        }
        String normalized = ImportFileParser.normalizeHeader(description);
        return Arrays.stream(rule.getPattern().split("\\|"))
                .map(ImportFileParser::normalizeHeader)
                .filter(p -> !p.isBlank())
                .anyMatch(normalized::contains);
    }

    /** Returns how many transactions were moved to the rule's category. */
    @Transactional
    public int applyRule(CategoryRule rule) {
        TransactionType type = rule.getCategory().getType();
        String fallbackName = type == TransactionType.EXPENSE ? "Otros gastos" : "Otros ingresos";
        Category fallback = categoryRepository.findAll().stream()
                .filter(c -> c.getType() == type && c.getName().equalsIgnoreCase(fallbackName))
                .findFirst()
                .orElse(null);
        if (fallback == null || fallback.getId().equals(rule.getCategory().getId())) {
            return 0;
        }
        // An account-owned target category can only take transactions of that
        // same account; a global one takes them regardless of account.
        Long ownerAccountId = rule.getCategory().getAccount() != null
                ? rule.getCategory().getAccount().getId() : null;
        List<Transaction> candidates = transactionRepository.findByCategoryId(fallback.getId());
        int count = 0;
        for (Transaction transaction : candidates) {
            if (ownerAccountId != null && !transaction.getAccount().getId().equals(ownerAccountId)) {
                continue;
            }
            if (matches(rule, transaction.getDescription())) {
                transaction.setCategory(rule.getCategory());
                count++;
            }
        }
        transactionRepository.saveAll(candidates);
        return count;
    }
}
