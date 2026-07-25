package com.xroig.finance.investments.domain;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Outbound port: how the application persists and reads {@link PriceQuote} values.
 * The write is an <b>upsert</b> on the natural key (security, date): a newer value
 * for the same key overwrites, never duplicates (RN-9). The valuation reads the
 * latest quote ≤ the valuation date (RN-6).
 */
public interface PriceQuoteRepository {

    /** Inserts or overwrites the quote of its (security, date) key (RN-9). */
    void upsert(PriceQuote quote);

    Optional<PriceQuote> find(SecurityId securityId, LocalDate quoteDate);

    /** Latest available quote ≤ {@code date} for the security (RN-6). */
    Optional<PriceQuote> findLatestOnOrBefore(SecurityId securityId, LocalDate date);
}
