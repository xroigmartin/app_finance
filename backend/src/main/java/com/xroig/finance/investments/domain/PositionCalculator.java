package com.xroig.finance.investments.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Domain service that computes positions, average capitalized cost, realized P&L
 * and portfolio cash from the raw transactions — nothing is ever stored (§3).
 *
 * <p>Rules implemented: quantities and cash are direct sums of signed values
 * (RN-2/RN-3, the sign convention lives in the data); the acquisition cost
 * capitalizes |amount| + |fee| + |trade_tax| (RN-3), linking each {@code TRADE_TAX}
 * row to the trade of its security+date (§9) — capitalized on a buy date,
 * reducing net proceeds on a sell date; a split is a quantity delta at zero cost;
 * every cost component converts to the base currency with its entry's own snapshot
 * (RN-7a), falling back to the rate table (RN-7b) and, as a last resort, 1:1 plus a
 * warning. Sales beyond the held position (precision-tolerant, RN-4) are flagged
 * but processed. Within one date, acquisitions process before disposals so a
 * same-day buy+sell does not raise a spurious warning.
 */
public class PositionCalculator {

    private static final int AVERAGE_SCALE = 8;

    public PortfolioPositions calculate(String baseCurrency,
                                        List<InvestmentTransaction> transactions,
                                        CurrencyConverter rates) {
        String base = IsoCurrency.require(baseCurrency);
        List<PositionWarning> warnings = new ArrayList<>();

        List<InvestmentTransaction> ordered = transactions.stream()
                .sorted(Comparator.comparing(InvestmentTransaction::tradeDate)
                        .thenComparing(tx -> isAcquisition(tx) ? 0 : 1))
                .toList();

        Map<TradeKey, CurrencyMoney> tradeTaxBuckets = collectTradeTaxes(ordered, base, rates, warnings);
        Map<SecurityId, MutablePosition> positions = new LinkedHashMap<>();

        for (InvestmentTransaction tx : ordered) {
            switch (tx.type()) {
                case BUY -> applyBuy(positions, tx, tradeTaxBuckets, base, rates, warnings);
                case SELL -> applySell(positions, tx, tradeTaxBuckets, base, rates, warnings);
                case SPLIT -> positionOf(positions, tx.securityId(), base).addQuantity(tx.quantity());
                default -> { /* solo efectivo: no toca posiciones */ }
            }
        }

        return new PortfolioPositions(
                positions.entrySet().stream()
                        .map(entry -> entry.getValue().toPosition(entry.getKey()))
                        .toList(),
                cashByCurrency(transactions),
                List.copyOf(warnings));
    }

    /** Efectivo por divisa (RN-2): suma directa de importes firmados, cada uno en su divisa efectiva. */
    private Map<String, CurrencyMoney> cashByCurrency(List<InvestmentTransaction> transactions) {
        Map<String, CurrencyMoney> cash = new LinkedHashMap<>();
        for (InvestmentTransaction tx : transactions) {
            addCash(cash, tx.amount());
            addCash(cash, tx.counterAmount());
            addCash(cash, tx.fee());
            addCash(cash, tx.tax());
        }
        return cash;
    }

    private static void addCash(Map<String, CurrencyMoney> cash, CurrencyMoney value) {
        if (value != null) {
            cash.merge(value.currency(), value, CurrencyMoney::add);
        }
    }

    /** Agrupa las filas TRADE_TAX por (instrumento, fecha) en base, como magnitud positiva (§9). */
    private Map<TradeKey, CurrencyMoney> collectTradeTaxes(List<InvestmentTransaction> ordered, String base,
                                                           CurrencyConverter rates, List<PositionWarning> warnings) {
        Map<TradeKey, CurrencyMoney> buckets = new LinkedHashMap<>();
        for (InvestmentTransaction tx : ordered) {
            if (tx.type() == InvestmentTransactionType.TRADE_TAX) {
                buckets.merge(new TradeKey(tx.securityId(), tx.tradeDate()),
                        toBase(tx.amount().abs(), tx, base, rates, warnings),
                        CurrencyMoney::add);
            }
        }
        return buckets;
    }

    private void applyBuy(Map<SecurityId, MutablePosition> positions, InvestmentTransaction tx,
                          Map<TradeKey, CurrencyMoney> tradeTaxBuckets, String base,
                          CurrencyConverter rates, List<PositionWarning> warnings) {
        MutablePosition position = positionOf(positions, tx.securityId(), base);
        CurrencyMoney capitalized = toBase(tx.amount().abs(), tx, base, rates, warnings)
                .add(feeToBase(tx, base, rates, warnings).abs())
                .add(consumeTradeTax(tradeTaxBuckets, tx, base));
        position.addQuantity(tx.quantity());
        position.cost = position.cost.add(capitalized);
    }

    private void applySell(Map<SecurityId, MutablePosition> positions, InvestmentTransaction tx,
                           Map<TradeKey, CurrencyMoney> tradeTaxBuckets, String base,
                           CurrencyConverter rates, List<PositionWarning> warnings) {
        MutablePosition position = positionOf(positions, tx.securityId(), base);
        Quantity sold = tx.quantity().abs();

        if (sold.exceeds(position.quantity)) {
            warnings.add(new PositionWarning(tx.securityId(), tx.tradeDate(),
                    "venta sin posición suficiente: falta histórico anterior (RN-4)"));
        }

        // Coste de lo vendido por promedio, sobre la parte cubierta por la posición.
        BigDecimal covered = sold.value().min(position.quantity.value().max(BigDecimal.ZERO));
        CurrencyMoney costOfSold = CurrencyMoney.zero(base);
        if (position.quantity.exceeds(Quantity.ZERO)) {
            BigDecimal average = position.cost.amount()
                    .divide(position.quantity.value(), AVERAGE_SCALE, RoundingMode.HALF_UP);
            costOfSold = CurrencyMoney.of(average.multiply(covered), base);
        }

        // Neto percibido: importe − comisión − trade tax del día (RN-3, §9).
        CurrencyMoney netProceeds = toBase(tx.amount(), tx, base, rates, warnings)
                .add(feeToBase(tx, base, rates, warnings))
                .subtract(consumeTradeTax(tradeTaxBuckets, tx, base));

        CurrencyMoney realizedOnSale = netProceeds.subtract(costOfSold);
        position.realized = position.realized.add(realizedOnSale);
        position.realizedByYear.merge(tx.tradeDate().getYear(), realizedOnSale, CurrencyMoney::add);
        position.addQuantity(tx.quantity());
        position.cost = position.cost.subtract(costOfSold);
        if (!position.quantity.exceeds(Quantity.ZERO)) {
            position.cost = CurrencyMoney.zero(base); // posición cerrada (o negativa): sin coste vivo
        }
    }

    private static MutablePosition positionOf(Map<SecurityId, MutablePosition> positions,
                                              SecurityId securityId, String base) {
        return positions.computeIfAbsent(securityId, id -> new MutablePosition(base));
    }

    private static CurrencyMoney consumeTradeTax(Map<TradeKey, CurrencyMoney> buckets,
                                                 InvestmentTransaction tx, String base) {
        CurrencyMoney bucket = buckets.remove(new TradeKey(tx.securityId(), tx.tradeDate()));
        return bucket == null ? CurrencyMoney.zero(base) : bucket;
    }

    private CurrencyMoney feeToBase(InvestmentTransaction tx, String base,
                                    CurrencyConverter rates, List<PositionWarning> warnings) {
        if (tx.fee() == null) {
            return CurrencyMoney.zero(base);
        }
        return toBase(tx.fee(), tx, base, rates, warnings);
    }

    /**
     * Convierte un componente a divisa base: con el snapshot del propio apunte si la
     * divisa coincide con la del apunte (RN-7a); si no, con la tabla de tipos al
     * último tipo ≤ fecha (RN-7b); en último término 1:1 con warning.
     */
    private CurrencyMoney toBase(CurrencyMoney value, InvestmentTransaction tx, String base,
                                 CurrencyConverter rates, List<PositionWarning> warnings) {
        if (value.currency().equals(base)) {
            return value;
        }
        if (tx.fxRateToBase() != null && value.currency().equals(tx.currency())) {
            return CurrencyMoney.of(value.amount().multiply(tx.fxRateToBase()), base);
        }
        return rates.convert(value, base, tx.tradeDate()).orElseGet(() -> {
            warnings.add(new PositionWarning(tx.securityId(), tx.tradeDate(),
                    "sin tipo de cambio " + value.currency() + "→" + base
                            + " a " + tx.tradeDate() + ": importe sin convertir (1:1)"));
            return CurrencyMoney.of(value.amount(), base);
        });
    }

    private static boolean isAcquisition(InvestmentTransaction tx) {
        return tx.type() == InvestmentTransactionType.BUY
                || tx.type() == InvestmentTransactionType.SPLIT;
    }

    private record TradeKey(SecurityId securityId, LocalDate tradeDate) {
    }

    private static final class MutablePosition {

        private Quantity quantity = Quantity.ZERO;
        private CurrencyMoney cost;
        private CurrencyMoney realized;
        private final Map<Integer, CurrencyMoney> realizedByYear = new LinkedHashMap<>();

        private MutablePosition(String base) {
            this.cost = CurrencyMoney.zero(base);
            this.realized = CurrencyMoney.zero(base);
        }

        private void addQuantity(Quantity delta) {
            quantity = quantity.add(delta);
        }

        private Position toPosition(SecurityId securityId) {
            return new Position(securityId, quantity, cost, realized, Map.copyOf(realizedByYear));
        }
    }
}
