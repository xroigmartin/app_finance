package com.xroig.finance.investments.domain;

/**
 * Outbound port: how the application persists {@link ImportRecord}s. Write-only
 * from the domain's side (a log) — the paginated read is CQRS, not this port's
 * responsibility (same split as {@link InvestmentTransactionRepository#save} vs.
 * the query port for listings).
 */
public interface ImportRecordRepository {

    ImportRecord save(ImportRecord record);
}
