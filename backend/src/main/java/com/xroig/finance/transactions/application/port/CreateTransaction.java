package com.xroig.finance.transactions.application.port;

import com.xroig.finance.shared.domain.TransactionType;
import com.xroig.finance.transactions.application.TransactionView;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Inbound port: create a movement, normal or a refund of an expense. */
public interface CreateTransaction {

    TransactionView create(TransactionCommand command);

    /**
     * Intent to create/update a movement. When {@code refundOfId} is set the movement
     * is a refund (its {@code type}/{@code accountId}/{@code categoryId} are inherited
     * from the original and ignored here).
     */
    record TransactionCommand(LocalDate date, BigDecimal amount, String description,
                              TransactionType type, Long accountId, Long categoryId, Long refundOfId) {
    }
}
