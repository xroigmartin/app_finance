package com.xroig.finance.investments.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

/**
 * Computed position of one security (RN-3): direct sum of signed quantities, cost
 * basis capitalized with the purchase costs (|amount| + |fee| + |trade_tax|) in the
 * portfolio's base currency, and the realized P&L accumulated by sales (net
 * proceeds − average capitalized cost of the sold titles), both as a running total
 * and bucketed by the calendar year of each sale. Never stored — always
 * recomputed from the transactions.
 */
public record Position(SecurityId securityId, Quantity quantity,
                       CurrencyMoney costBasis, CurrencyMoney realizedPnl,
                       Map<Integer, CurrencyMoney> realizedByYear) {

    /**
     * Average capitalized cost per title, or null when the quantity is not positive
     * beyond the precision tolerance (closed or negative position, RN-4).
     */
    public CurrencyMoney averageCost() {
        if (!quantity.exceeds(Quantity.ZERO)) {
            return null;
        }
        BigDecimal average = costBasis.amount().divide(quantity.value(), 4, RoundingMode.HALF_UP);
        return CurrencyMoney.of(average, costBasis.currency());
    }
}
