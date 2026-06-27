package com.xroig.finance.reporting.application;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Read-side (CQRS) outbound port: transfer totals into/out of an account up to a date,
 * needed to compute account balances. Implemented by an adapter in {@code infrastructure}.
 */
public interface TransferAggregateQuery {

    /** Total transferred <em>into</em> the account up to (and including) {@code until}. */
    BigDecimal inUntil(Long accountId, LocalDate until);

    /** Total transferred <em>out of</em> the account up to (and including) {@code until}. */
    BigDecimal outUntil(Long accountId, LocalDate until);
}
