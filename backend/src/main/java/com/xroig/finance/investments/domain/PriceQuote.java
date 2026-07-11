package com.xroig.finance.investments.domain;

import com.xroig.finance.shared.domain.ValidationException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/**
 * Closing price of one security on one date, in the security's quote currency,
 * normalized to the price scale of the context (8 decimals, {@code HALF_UP}).
 * Fed from the Flex report's open positions on import.
 *
 * <p>Value-based: the natural key (security, date) is unique and a newer value for
 * the same key overwrites (upsert, RN-9) — that contract lives in the repository
 * and is verified at the persistence adapter.
 */
public record PriceQuote(SecurityId securityId, LocalDate quoteDate, BigDecimal price) {

    private static final int SCALE = 8;

    public PriceQuote {
        if (securityId == null) {
            throw new ValidationException("La cotización requiere un instrumento");
        }
        if (quoteDate == null) {
            throw new ValidationException("La cotización requiere una fecha");
        }
        if (price == null || price.signum() <= 0) {
            throw new ValidationException("El precio de una cotización debe ser positivo");
        }
        price = price.setScale(SCALE, RoundingMode.HALF_UP);
    }

    public static PriceQuote of(SecurityId securityId, LocalDate quoteDate, BigDecimal price) {
        return new PriceQuote(securityId, quoteDate, price);
    }

    public static PriceQuote of(SecurityId securityId, LocalDate quoteDate, String price) {
        return new PriceQuote(securityId, quoteDate, price == null ? null : new BigDecimal(price));
    }
}
