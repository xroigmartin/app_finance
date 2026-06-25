package com.xroig.finance.transactions.application.port;

import com.xroig.finance.transactions.application.TransactionView;

import java.time.LocalDate;
import java.util.List;

/** Inbound port: read movements (filtered search and the recent list). */
public interface FindTransactions {

    List<TransactionView> search(LocalDate from, LocalDate to, Long accountId, Long categoryId);

    List<TransactionView> recent();
}
