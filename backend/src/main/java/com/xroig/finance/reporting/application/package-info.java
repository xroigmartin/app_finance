/**
 * Reporting bounded context — application layer (read-only, CQRS). The dashboard never
 * goes through the write aggregates: the inbound facade {@link
 * com.xroig.finance.reporting.application.port.DashboardReports} is implemented by {@link
 * com.xroig.finance.reporting.application.ReportingService}, which keeps the aggregation
 * maths (balances, savings, deltas, yield %, net-worth series, budget roll-up) and reads
 * the raw figures through the outbound query ports ({@link
 * com.xroig.finance.reporting.application.MovementAggregateQuery}, {@link
 * com.xroig.finance.reporting.application.TransferAggregateQuery}, {@link
 * com.xroig.finance.reporting.application.AccountCatalogQuery}, {@link
 * com.xroig.finance.reporting.application.BudgetCatalogQuery}). The {@code *View} records
 * are the read models the API exposes, faithful to the legacy JSON.
 */
package com.xroig.finance.reporting.application;
