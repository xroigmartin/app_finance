package com.xroig.finance.investments.application.port;

import com.xroig.finance.investments.application.InvestmentTransactionView;
import com.xroig.finance.investments.domain.InvestmentTransactionType;
import com.xroig.finance.shared.domain.Page;

import java.time.LocalDate;

/**
 * Inbound port: list a portfolio's operations filtered by type, date range and
 * instrument (§6), newest first, paginated.
 */
public interface FindInvestmentTransactions {

    Page<InvestmentTransactionView> find(long portfolioId, TransactionFilter filter, int page, int size);

    /** Optional filters; a null field does not filter. */
    record TransactionFilter(InvestmentTransactionType type, LocalDate from, LocalDate to,
                             Long securityId) {

        public static TransactionFilter none() {
            return new TransactionFilter(null, null, null, null);
        }
    }
}
