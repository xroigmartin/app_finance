package com.xroig.finance.reporting.infrastructure.persistence;

import com.xroig.finance.reporting.application.TransferAggregateQuery;
import com.xroig.finance.repository.TransferRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Resolves {@link TransferAggregateQuery} with the transfer in/out totals. */
@Component
class TransferAggregateQueryAdapter implements TransferAggregateQuery {

    private final TransferRepository transfers;

    TransferAggregateQueryAdapter(TransferRepository transfers) {
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
