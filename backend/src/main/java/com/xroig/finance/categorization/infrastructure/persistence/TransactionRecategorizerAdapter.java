package com.xroig.finance.categorization.infrastructure.persistence;

import com.xroig.finance.accounts.domain.AccountId;
import com.xroig.finance.categories.domain.CategoryId;
import com.xroig.finance.categories.infrastructure.persistence.CategoryJpaRepository;
import com.xroig.finance.categorization.domain.TransactionRecategorizer;
import com.xroig.finance.transactions.domain.TransactionId;
import com.xroig.finance.transactions.infrastructure.persistence.TransactionJpaEntity;
import com.xroig.finance.transactions.infrastructure.persistence.TransactionJpaRepository;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;

/**
 * Adapter for {@link TransactionRecategorizer} (an anti-corruption boundary over the
 * transactions store): lists the movements in a fallback category and moves the chosen
 * ones to the rule's category. It mutates only the movements the application selected, so
 * a category assigned explicitly or by another rule is never overwritten.
 */
@Component
public class TransactionRecategorizerAdapter implements TransactionRecategorizer {

    private final TransactionJpaRepository transactions;
    private final CategoryJpaRepository categories;

    public TransactionRecategorizerAdapter(TransactionJpaRepository transactions,
                                           CategoryJpaRepository categories) {
        this.transactions = transactions;
        this.categories = categories;
    }

    @Override
    public List<RecategorizationCandidate> candidatesIn(CategoryId fallbackCategory) {
        return transactions.findByCategoryId(fallbackCategory.value()).stream()
                .map(TransactionRecategorizerAdapter::toCandidate)
                .toList();
    }

    @Override
    public void reassign(Collection<TransactionId> transactionIds, CategoryId target) {
        if (transactionIds.isEmpty()) {
            return;
        }
        List<Long> ids = transactionIds.stream().map(TransactionId::value).toList();
        List<TransactionJpaEntity> entities = transactions.findAllById(ids);
        entities.forEach(e -> e.setCategory(categories.getReferenceById(target.value())));
        transactions.saveAll(entities);
    }

    private static RecategorizationCandidate toCandidate(TransactionJpaEntity entity) {
        return new RecategorizationCandidate(new TransactionId(entity.getId()),
                entity.getDescription(), new AccountId(entity.getAccount().getId()));
    }
}
