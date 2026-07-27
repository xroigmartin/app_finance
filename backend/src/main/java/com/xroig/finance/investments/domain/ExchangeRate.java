package com.xroig.finance.investments.domain;

import com.xroig.finance.shared.domain.ValidationException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/**
 * Daily exchange rate, normalized to the single direction currency→EUR: EUR is the
 * pivot and any pair is derived arithmetically ({@code from→to = (from→EUR) ÷
 * (to→EUR)}, RN-7), so a portfolio may have any base currency without widening the
 * table. Fed from the Flex report's conversion rates on import.
 *
 * <p>Value-based, rate at the context's rate scale (8 decimals, {@code HALF_UP}).
 * Uniqueness per (date, pair) and upsert semantics (RN-9) are the repository's
 * contract, verified at the persistence adapter.
 */
public record ExchangeRate(LocalDate rateDate, String fromCurrency, String toCurrency, BigDecimal rate) {

    private static final int SCALE = 8;
    public static final String PIVOT = "EUR";

    public ExchangeRate {
        if (rateDate == null) {
            throw new ValidationException("El tipo de cambio requiere una fecha");
        }
        fromCurrency = IsoCurrency.require(fromCurrency);
        toCurrency = IsoCurrency.require(toCurrency);
        if (!PIVOT.equals(toCurrency)) {
            throw new ValidationException("Los tipos de cambio se normalizan a la dirección divisa→EUR");
        }
        if (PIVOT.equals(fromCurrency)) {
            throw new ValidationException("El tipo EUR→EUR no se almacena (EUR es el pivote)");
        }
        if (rate == null || rate.signum() <= 0) {
            throw new ValidationException("El tipo de cambio debe ser positivo");
        }
        rate = rate.setScale(SCALE, RoundingMode.HALF_UP);
    }

    public static ExchangeRate toEur(LocalDate rateDate, String fromCurrency, BigDecimal rate) {
        return new ExchangeRate(rateDate, fromCurrency, PIVOT, rate);
    }

    public static ExchangeRate toEur(LocalDate rateDate, String fromCurrency, String rate) {
        return new ExchangeRate(rateDate, fromCurrency, PIVOT, rate == null ? null : new BigDecimal(rate));
    }
}
