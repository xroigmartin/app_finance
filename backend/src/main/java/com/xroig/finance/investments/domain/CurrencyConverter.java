package com.xroig.finance.investments.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Domain service for valuation-date conversion (RN-7b): converts an amount to a
 * target currency using the latest stored rate ≤ the requested date. Rates are
 * stored in the single direction currency→EUR and EUR acts as the pivot, so any
 * pair is derived arithmetically ({@code from→to = (from→EUR) ÷ (to→EUR)}).
 *
 * <p>A missing rate yields {@link Optional#empty()}: the read side decides how to
 * degrade (RN-6 style), never this service. Amounts fixed in the past convert with
 * the transaction's own {@code fx_rate_to_base} snapshot instead (RN-7a).
 */
public class CurrencyConverter {

    private static final int DIVISION_SCALE = 12;

    private final Map<String, NavigableMap<LocalDate, BigDecimal>> ratesToEurByCurrency = new HashMap<>();

    public CurrencyConverter(Collection<ExchangeRate> rates) {
        for (ExchangeRate rate : rates) {
            ratesToEurByCurrency
                    .computeIfAbsent(rate.fromCurrency(), currency -> new TreeMap<>())
                    .put(rate.rateDate(), rate.rate());
        }
    }

    /** Converts to the target currency with the latest rate ≤ {@code date}; empty if a rate is missing. */
    /**
     * Conversión de un importe <b>fijado</b> de un apunte a la divisa base: el
     * snapshot del propio apunte si aplica a su divisa (RN-7a), después el último
     * tipo ≤ fecha de la tabla (RN-7b) y, en último término, 1:1 — la degradación
     * silenciosa compartida por rentas, rentabilidad y read-side.
     */
    public CurrencyMoney fixedToBase(CurrencyMoney value, InvestmentTransaction tx, String baseCurrency) {
        if (value.currency().equals(baseCurrency)) {
            return value;
        }
        if (tx.fxRateToBase() != null && value.currency().equals(tx.currency())) {
            return CurrencyMoney.of(value.amount().multiply(tx.fxRateToBase()), baseCurrency);
        }
        return convert(value, baseCurrency, tx.tradeDate())
                .orElseGet(() -> CurrencyMoney.of(value.amount(), baseCurrency));
    }

    public Optional<CurrencyMoney> convert(CurrencyMoney money, String toCurrency, LocalDate date) {
        String target = IsoCurrency.require(toCurrency);
        if (money.currency().equals(target)) {
            return Optional.of(money);
        }
        return rateToEur(money.currency(), date)
                .flatMap(fromRate -> rateToEur(target, date)
                        .map(toRate -> money.amount().multiply(fromRate)
                                .divide(toRate, DIVISION_SCALE, RoundingMode.HALF_UP)))
                .map(amount -> CurrencyMoney.of(amount, target));
    }

    private Optional<BigDecimal> rateToEur(String currency, LocalDate date) {
        if (ExchangeRate.PIVOT.equals(currency)) {
            return Optional.of(BigDecimal.ONE);
        }
        NavigableMap<LocalDate, BigDecimal> series = ratesToEurByCurrency.get(currency);
        if (series == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(series.floorEntry(date)).map(Map.Entry::getValue);
    }
}
