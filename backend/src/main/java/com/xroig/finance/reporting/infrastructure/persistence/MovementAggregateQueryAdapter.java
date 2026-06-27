package com.xroig.finance.reporting.infrastructure.persistence;

import com.xroig.finance.reporting.application.MovementAggregateQuery;
import com.xroig.finance.repository.TransactionRepository;
import com.xroig.finance.shared.domain.TransactionType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Resolves {@link MovementAggregateQuery} with the movement aggregation queries. */
@Component
class MovementAggregateQueryAdapter implements MovementAggregateQuery {

    private final TransactionRepository transactions;

    MovementAggregateQueryAdapter(TransactionRepository transactions) {
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
