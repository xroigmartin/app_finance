package com.xroig.finance.reporting.infrastructure.persistence;

import com.xroig.finance.reporting.application.MovementAggregateQuery;
import com.xroig.finance.shared.domain.TransactionType;
import com.xroig.finance.transactions.infrastructure.persistence.TransactionJpaRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Resolves {@link MovementAggregateQuery} with the transactions context's aggregation queries. */
@Component
class MovementAggregateQueryAdapter implements MovementAggregateQuery {

    private final TransactionJpaRepository transactions;

    MovementAggregateQueryAdapter(TransactionJpaRepository transactions) {
        this.transactions = transactions;
    }

    @Override
    public BigDecimal sumByType(TransactionType type, LocalDate from, LocalDate to, Long accountId) {
        return transactions.sumByTypeAndPeriod(type, from, to, accountId);
    }

    @Override
    public BigDecimal netByAccountUntil(Long accountId, LocalDate until) {
        return transactions.netTotalByAccountUntil(accountId, until);
    }

    @Override
    public BigDecimal spentByCategoryTree(Long categoryId, LocalDate from, LocalDate to, Long accountId) {
        return transactions.sumByCategoryTreeAndPeriod(categoryId, from, to, accountId);
    }

    @Override
    public List<CategoryShare> shareByCategory(TransactionType type, LocalDate from, LocalDate to, Long accountId) {
        return transactions.sumByCategory(type, from, to, accountId).stream()
                .map(row -> new CategoryShare((String) row[0], (String) row[1], (BigDecimal) row[2]))
                .toList();
    }
}
