package com.xroig.finance.investments.domain;

import com.xroig.finance.shared.domain.ValidationException;

import java.util.Currency;
import java.util.Locale;

/**
 * ISO 4217 currency codes: the normalization (uppercase) and validation shared by
 * the value objects of the investments context. Validates against the JVM's ISO
 * 4217 registry.
 */
final class IsoCurrency {

    private IsoCurrency() {
    }

    /** Normalizes to uppercase and validates against ISO 4217; {@link ValidationException} otherwise. */
    static String require(String currency) {
        if (currency == null || currency.isBlank()) {
            throw new ValidationException("Se requiere una divisa ISO 4217");
        }
        String normalized = currency.toUpperCase(Locale.ROOT);
        try {
            Currency.getInstance(normalized);
        } catch (IllegalArgumentException e) {
            throw new ValidationException("Divisa no reconocida (ISO 4217): " + currency);
        }
        return normalized;
    }
}
