package com.xroig.finance.investments.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Outbound port: how the application persists and reads {@link ExchangeRate}
 * values (stored currency→EUR, RN-7). The write is an <b>upsert</b> on the natural
 * key (date, pair): a newer value for the same key overwrites, never duplicates
 * (RN-9). {@link #findAll()} feeds the {@link CurrencyConverter} of the read side.
 */
public interface ExchangeRateRepository {

    /** Inserts or overwrites the rate of its (date, pair) key (RN-9). */
    void upsert(ExchangeRate rate);

    Optional<ExchangeRate> find(LocalDate rateDate, String fromCurrency, String toCurrency);

    List<ExchangeRate> findAll();
}
