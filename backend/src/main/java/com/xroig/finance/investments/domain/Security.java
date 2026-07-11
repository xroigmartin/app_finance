package com.xroig.finance.investments.domain;

import com.xroig.finance.shared.domain.ValidationException;

import java.util.Locale;

/**
 * Security aggregate root: a tradable instrument (stock, ETF, fund…). Its business
 * identity is the pair ISIN + quote currency (a cross-listed instrument in another
 * currency is another security, §9); {@code ticker}, {@code type}, {@code exchange}
 * and {@code figi} are non-identity metadata, captured for the future price API.
 *
 * <p>Reimports refresh the non-identity metadata (RN-9) — a missing incoming value
 * keeps the current one (refresh never erases) — while ISIN and currency never
 * change. Pure of any framework or persistence concern.
 */
public class Security {

    private final SecurityId id;
    private final String isin;
    private final String currency;
    private String name;
    private String ticker;
    private String type;
    private String exchange;
    private String figi;

    private Security(SecurityId id, String isin, String currency, String name,
                     String ticker, String type, String exchange, String figi) {
        this.id = id;
        this.isin = requireIsin(isin);
        this.currency = IsoCurrency.require(currency);
        this.name = requireName(name);
        this.ticker = ticker;
        this.type = type;
        this.exchange = exchange;
        this.figi = figi;
    }

    /** Factory for a new security (identity assigned on persist). */
    public static Security create(String isin, String currency, String name,
                                  String ticker, String type, String exchange, String figi) {
        return new Security(null, isin, currency, name, ticker, type, exchange, figi);
    }

    /** Rebuilds a persisted security (identity present), from the persistence adapter. */
    public static Security rehydrate(SecurityId id, String isin, String currency, String name,
                                     String ticker, String type, String exchange, String figi) {
        if (id == null) {
            throw new IllegalArgumentException("Un security rehidratado necesita identidad");
        }
        return new Security(id, isin, currency, name, ticker, type, exchange, figi);
    }

    /**
     * Refreshes the non-identity metadata on reimport (RN-9). A null/blank incoming
     * value keeps the current one; ISIN and currency are never touched.
     */
    public void refreshMetadata(String name, String ticker, String exchange, String figi) {
        if (name != null && !name.isBlank()) {
            this.name = name;
        }
        if (ticker != null && !ticker.isBlank()) {
            this.ticker = ticker;
        }
        if (exchange != null && !exchange.isBlank()) {
            this.exchange = exchange;
        }
        if (figi != null && !figi.isBlank()) {
            this.figi = figi;
        }
    }

    /** Changes the (free-text) instrument type; a missing incoming value keeps the current one. */
    public void changeType(String type) {
        if (type != null && !type.isBlank()) {
            this.type = type;
        }
    }

    public SecurityId id() {
        return id;
    }

    public String isin() {
        return isin;
    }

    public String currency() {
        return currency;
    }

    public String name() {
        return name;
    }

    public String ticker() {
        return ticker;
    }

    public String type() {
        return type;
    }

    public String exchange() {
        return exchange;
    }

    public String figi() {
        return figi;
    }

    private static String requireIsin(String isin) {
        if (isin == null || isin.isBlank()) {
            throw new ValidationException("El instrumento requiere un ISIN");
        }
        return isin.trim().toUpperCase(Locale.ROOT);
    }

    private static String requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new ValidationException("El instrumento requiere un nombre");
        }
        return name;
    }
}
