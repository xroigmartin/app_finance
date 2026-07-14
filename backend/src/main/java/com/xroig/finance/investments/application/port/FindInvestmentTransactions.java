package com.xroig.finance.investments.application.port;

import com.xroig.finance.investments.application.InvestmentTransactionView;
import com.xroig.finance.investments.domain.InvestmentTransactionType;

import java.time.LocalDate;
import java.util.List;

/**
 * Inbound port: list a portfolio's operations filtered by type, date range and
 * instrument (§6 — no pagination in v1), newest first.
 */
public interface FindInvestmentTransactions {

    List<InvestmentTransactionView> find(long portfolioId, TransactionFilter filter);

    /** Optional filters; a null field does not filter. */
    record TransactionFilter(InvestmentTransactionType type, LocalDate from, LocalDate to,
                             Long securityId) {

        public static TransactionFilter none() {
            return new TransactionFilter(null, null, null, null);
        }
    }
}
