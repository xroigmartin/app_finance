package com.xroig.finance.investments.domain;

import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Domain service that aggregates the portfolio's income (RF-7) from the raw
 * transactions: dividends/interest per instrument and month in gross, the net
 * after the {@code TAX} withholding linked by instrument (§9), and the fees and
 * withholdings paid per month. {@code TRADE_TAX} is acquisition cost (RN-3) and
 * never enters the income view. Nothing is stored; fixed amounts convert to the
 * base currency with each entry's own snapshot (RN-7a).
 */
public class IncomeCalculator {

    public IncomeStatement calculate(String baseCurrency,
                                     List<InvestmentTransaction> transactions,
                                     CurrencyConverter rates) {
        String base = IsoCurrency.require(baseCurrency);
        Map<IncomeKey, CurrencyMoney> gross = new LinkedHashMap<>();
        Map<IncomeKey, CurrencyMoney> withheld = new LinkedHashMap<>();
        Map<YearMonth, CurrencyMoney> feesByMonth = new LinkedHashMap<>();
        Map<YearMonth, CurrencyMoney> taxesByMonth = new LinkedHashMap<>();

        for (InvestmentTransaction tx : transactions) {
            IncomeKey key = new IncomeKey(tx.securityId(), YearMonth.from(tx.tradeDate()));
            switch (tx.type()) {
                case DIVIDEND, INTEREST -> {
                    gross.merge(key, rates.fixedToBase(tx.amount(), tx, base), CurrencyMoney::add);
                    // Retención embebida en el propio apunte de renta (p. ej. alta manual).
                    if (tx.tax() != null) {
                        addWithholding(withheld, taxesByMonth, key,
                                rates.fixedToBase(tx.tax(), tx, base).negate());
                    }
                }
                // negate(), no abs(): TAX admite reversas de bróker con el signo invertido
                // (§11 PRD Inversiones) — sumar los importes con signo (negados a magnitud
                // positiva) antes de acumular hace que una reversa cancele el original en
                // vez de sumarse a él, que es lo que haría abs().
                case TAX -> addWithholding(withheld, taxesByMonth, key,
                        rates.fixedToBase(tx.amount(), tx, base).negate());
                case FEE -> feesByMonth.merge(key.month(),
                        rates.fixedToBase(tx.amount(), tx, base).negate(), CurrencyMoney::add);
                default -> { /* el resto no es renta (TRADE_TAX es coste de adquisición, §9) */ }
            }
            // La comisión de cualquier apunte es comisión pagada del periodo (RF-7).
            if (tx.fee() != null) {
                feesByMonth.merge(key.month(),
                        rates.fixedToBase(tx.fee(), tx, base).negate(), CurrencyMoney::add);
            }
        }

        List<InstrumentIncome> incomes = gross.entrySet().stream()
                .map(entry -> {
                    IncomeKey key = entry.getKey();
                    CurrencyMoney taxes = withheld.getOrDefault(key, CurrencyMoney.zero(base));
                    return new InstrumentIncome(key.securityId(), key.month(),
                            entry.getValue(), taxes, entry.getValue().subtract(taxes));
                })
                .toList();
        return new IncomeStatement(incomes, Map.copyOf(feesByMonth), Map.copyOf(taxesByMonth));
    }

    /** Una retención suma al bucket del instrumento (si lo hay) y al total del mes. */
    private static void addWithholding(Map<IncomeKey, CurrencyMoney> withheld,
                                       Map<YearMonth, CurrencyMoney> taxesByMonth,
                                       IncomeKey key, CurrencyMoney magnitude) {
        if (key.securityId() != null) {
            withheld.merge(key, magnitude, CurrencyMoney::add);
        }
        taxesByMonth.merge(key.month(), magnitude, CurrencyMoney::add);
    }

    private record IncomeKey(SecurityId securityId, YearMonth month) {
    }
}
