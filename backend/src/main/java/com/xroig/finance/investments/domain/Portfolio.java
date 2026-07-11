package com.xroig.finance.investments.domain;

import com.xroig.finance.shared.domain.ValidationException;

/**
 * Portfolio aggregate root: an investment portfolio fed by one broker account.
 * Its base currency (any ISO 4217) must match the broker account's base — the
 * {@code fxRateToBase} snapshots of the Flex point to it, so the import validates
 * the pair (§8) — and is therefore immutable after creation: changing it would
 * silently corrupt the RN-7a snapshots. Pure of any framework or persistence
 * concern.
 */
public class Portfolio {

    private final PortfolioId id;
    private String name;
    private final String baseCurrency;

    private Portfolio(PortfolioId id, String name, String baseCurrency) {
        this.id = id;
        this.name = requireName(name);
        this.baseCurrency = IsoCurrency.require(baseCurrency);
    }

    /** Factory for a new portfolio (identity assigned on persist). */
    public static Portfolio create(String name, String baseCurrency) {
        return new Portfolio(null, name, baseCurrency);
    }

    /** Rebuilds a persisted portfolio (identity present), from the persistence adapter. */
    public static Portfolio rehydrate(PortfolioId id, String name, String baseCurrency) {
        if (id == null) {
            throw new IllegalArgumentException("Una cartera rehidratada necesita identidad");
        }
        return new Portfolio(id, name, baseCurrency);
    }

    public void rename(String name) {
        this.name = requireName(name);
    }

    public PortfolioId id() {
        return id;
    }

    public String name() {
        return name;
    }

    public String baseCurrency() {
        return baseCurrency;
    }

    private static String requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new ValidationException("La cartera requiere un nombre");
        }
        return name;
    }
}
