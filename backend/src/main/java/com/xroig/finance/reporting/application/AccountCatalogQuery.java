package com.xroig.finance.reporting.application;

import java.math.BigDecimal;
import java.util.List;

/**
 * Read-side (CQRS) outbound port: the accounts and their initial balances, needed to
 * compute net worth. Implemented by an adapter in {@code infrastructure}.
 */
public interface AccountCatalogQuery {

    List<ReportAccount> all();

    /** An account as the dashboard reads it: identity, name, type and the stored initial balance. */
    record ReportAccount(Long id, String name, String type, BigDecimal initialBalance) {
    }
}
