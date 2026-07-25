package com.xroig.finance.reporting.infrastructure.persistence;

import com.xroig.finance.reporting.application.TransferAggregateQuery;
import com.xroig.finance.transfers.infrastructure.persistence.TransferJpaRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Resolves {@link TransferAggregateQuery} with the transfers context's in/out totals. */
@Component
class TransferAggregateQueryAdapter implements TransferAggregateQuery {

    private final TransferJpaRepository transfers;

    TransferAggregateQueryAdapter(TransferJpaRepository transfers) {
        this.transfers = transfers;
    }

    @Override
    public BigDecimal inUntil(Long accountId, LocalDate until) {
        return transfers.totalInUntil(accountId, until);
    }

    @Override
    public BigDecimal outUntil(Long accountId, LocalDate until) {
        return transfers.totalOutUntil(accountId, until);
    }
}
