package com.xroig.finance.investments.domain;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Domain service for the portfolio's money-weighted return (RN-8): XIRR as the
 * annualized IRR of the dated external cashflows plus the current value, solved
 * by Newton-Raphson with a bisection fallback. With quotes only at import dates
 * the result is an approximation over those points (§9). Returns empty when the
 * input admits no rate (fewer than two flows, all one sign, zero elapsed time)
 * or no root exists in the searchable range.
 */
public class PerformanceCalculator {

    private static final double DAYS_PER_YEAR = 365.0;
    private static final int MAX_NEWTON_ITERATIONS = 100;
    private static final int MAX_BISECTION_ITERATIONS = 200;
    private static final double TOLERANCE = 1e-10;
    /** (1+r) mínimo: una tasa no puede llegar a −100 %. */
    private static final double MIN_RATE = -0.999999;
    private static final double MAX_RATE = 1e6;
    private static final int RESULT_SCALE = 6;

    /** A dated cashflow, investor sign (contributions negative, proceeds positive). */
    public record Cashflow(LocalDate date, BigDecimal amount) {
    }

    /** The portfolio's value at a date, in the base currency (a valuation-series point). */
    public record ValuationPoint(LocalDate date, BigDecimal value) {
    }

    /**
     * Cumulative TWR (RN-8): chains the sub-period returns of the valuation
     * series, neutralizing the external flows ({@code DEPOSIT}/{@code WITHDRAWAL},
     * portfolio sign, converted RN-7a). The series' value at a flow date already
     * includes that day's flow, so each sub-period return is
     * {@code (V_i − F_i) / V_(i−1)} with {@code F_i} the net flow in
     * {@code (d_(i−1), d_i]}. {@code FX_TRADE} neither delimits sub-periods nor
     * counts as a flow. Empty with fewer than two points or a non-positive
     * sub-period start value. Not annualized: it is the period's return.
     */
    public Optional<BigDecimal> portfolioTwr(String baseCurrency,
                                             List<InvestmentTransaction> transactions,
                                             CurrencyConverter rates,
                                             List<ValuationPoint> valuations) {
        String base = IsoCurrency.require(baseCurrency);
        List<Cashflow> flows = transactions.stream()
                .filter(PerformanceCalculator::isExternalFlow)
                .map(tx -> new Cashflow(tx.tradeDate(), rates.fixedToBase(tx.amount(), tx, base).amount()))
                .toList();
        return twr(valuations, flows);
    }

    /**
     * Núcleo del TWR: encadena los subperiodos de la serie con flujos ya en
     * "signo entidad valorada" (dinero que entra en lo valorado, positivo). Lo
     * usa {@link #portfolioTwr} y el read-side para el TWR por posición, donde
     * el flujo es el efecto de caja de cada operación negado.
     */
    public Optional<BigDecimal> twr(List<ValuationPoint> valuations, List<Cashflow> flows) {
        List<ValuationPoint> series = valuations.stream()
                .sorted(java.util.Comparator.comparing(ValuationPoint::date))
                .toList();
        if (series.size() < 2) {
            return Optional.empty();
        }
        double factor = 1;
        for (int i = 1; i < series.size(); i++) {
            LocalDate from = series.get(i - 1).date();
            LocalDate to = series.get(i).date();
            double flow = flows.stream()
                    .filter(f -> f.date().isAfter(from) && !f.date().isAfter(to))
                    .mapToDouble(f -> f.amount().doubleValue())
                    .sum();
            double start = series.get(i - 1).value().doubleValue();
            if (start <= 0) {
                return Optional.empty();
            }
            factor *= (series.get(i).value().doubleValue() - flow) / start;
        }
        return Optional.of(new BigDecimal(factor - 1, MathContext.DECIMAL64)
                .setScale(RESULT_SCALE, java.math.RoundingMode.HALF_UP));
    }

    private static boolean isExternalFlow(InvestmentTransaction tx) {
        return tx.type() == InvestmentTransactionType.DEPOSIT
                || tx.type() == InvestmentTransactionType.WITHDRAWAL;
    }

    /**
     * XIRR of the portfolio (RN-8): the external flows — {@code DEPOSIT}/
     * {@code WITHDRAWAL} with the investor's sign (a contribution is money the
     * investor puts in, negative) — plus the current value at the valuation date.
     * {@code FX_TRADE} is not an external flow (it only changes the cash's
     * currency), nor is any instrument operation. Foreign flows convert to the
     * base with their own snapshot (RN-7a), the rate table (RN-7b) or 1:1.
     */
    public Optional<BigDecimal> portfolioXirr(String baseCurrency,
                                              List<InvestmentTransaction> transactions,
                                              CurrencyConverter rates,
                                              CurrencyMoney currentValue,
                                              LocalDate valuationDate) {
        String base = IsoCurrency.require(baseCurrency);
        List<Cashflow> flows = new ArrayList<>(transactions.stream()
                .filter(PerformanceCalculator::isExternalFlow)
                .map(tx -> new Cashflow(tx.tradeDate(),
                        rates.fixedToBase(tx.amount(), tx, base).amount().negate()))
                .toList());
        flows.add(new Cashflow(valuationDate, currentValue.amount()));
        return xirr(flows);
    }

    /** Annualized IRR of the flows; empty when no rate is solvable. */
    public Optional<BigDecimal> xirr(List<Cashflow> flows) {
        List<Cashflow> relevant = flows.stream()
                .filter(f -> f.amount().signum() != 0)
                .toList();
        if (relevant.size() < 2 || !hasBothSigns(relevant)) {
            return Optional.empty();
        }
        LocalDate origin = relevant.stream().map(Cashflow::date).min(LocalDate::compareTo).orElseThrow();
        double[] years = relevant.stream()
                .mapToDouble(f -> ChronoUnit.DAYS.between(origin, f.date()) / DAYS_PER_YEAR)
                .toArray();
        if (allZero(years)) {
            return Optional.empty();
        }
        double[] amounts = relevant.stream().mapToDouble(f -> f.amount().doubleValue()).toArray();

        return solve(amounts, years)
                .map(rate -> new BigDecimal(rate, MathContext.DECIMAL64)
                        .setScale(RESULT_SCALE, java.math.RoundingMode.HALF_UP));
    }

    private static boolean hasBothSigns(List<Cashflow> flows) {
        boolean positive = flows.stream().anyMatch(f -> f.amount().signum() > 0);
        boolean negative = flows.stream().anyMatch(f -> f.amount().signum() < 0);
        return positive && negative;
    }

    private static boolean allZero(double[] years) {
        for (double t : years) {
            if (t != 0) {
                return false;
            }
        }
        return true;
    }

    private static Optional<Double> solve(double[] amounts, double[] years) {
        Optional<Double> byNewton = newtonRaphson(amounts, years);
        return byNewton.isPresent() ? byNewton : bisection(amounts, years);
    }

    private static Optional<Double> newtonRaphson(double[] amounts, double[] years) {
        double rate = 0.1;
        for (int i = 0; i < MAX_NEWTON_ITERATIONS; i++) {
            double value = npv(amounts, years, rate);
            double derivative = npvDerivative(amounts, years, rate);
            if (derivative == 0 || !Double.isFinite(derivative)) {
                return Optional.empty();
            }
            double next = rate - value / derivative;
            if (next <= MIN_RATE || next > MAX_RATE || !Double.isFinite(next)) {
                return Optional.empty(); // fuera del dominio: que decida la bisección
            }
            if (Math.abs(next - rate) < TOLERANCE) {
                return Math.abs(npv(amounts, years, next)) < 1e-6 ? Optional.of(next) : Optional.empty();
            }
            rate = next;
        }
        return Optional.empty();
    }

    /** Fallback robusto: busca un cambio de signo ampliando el extremo superior y biseca. */
    private static Optional<Double> bisection(double[] amounts, double[] years) {
        double lo = MIN_RATE;
        double fLo = npv(amounts, years, lo);
        double hi = 1;
        double fHi = npv(amounts, years, hi);
        while (fLo * fHi > 0 && hi < MAX_RATE) {
            hi *= 10;
            fHi = npv(amounts, years, hi);
        }
        if (fLo * fHi > 0 || !Double.isFinite(fLo) || !Double.isFinite(fHi)) {
            return Optional.empty();
        }
        for (int i = 0; i < MAX_BISECTION_ITERATIONS; i++) {
            double mid = (lo + hi) / 2;
            double fMid = npv(amounts, years, mid);
            if (Math.abs(fMid) < TOLERANCE || (hi - lo) / 2 < TOLERANCE) {
                return Optional.of(mid);
            }
            if (fLo * fMid < 0) {
                hi = mid;
            } else {
                lo = mid;
                fLo = fMid;
            }
        }
        return Optional.of((lo + hi) / 2);
    }

    private static double npv(double[] amounts, double[] years, double rate) {
        double sum = 0;
        for (int i = 0; i < amounts.length; i++) {
            sum += amounts[i] / Math.pow(1 + rate, years[i]);
        }
        return sum;
    }

    private static double npvDerivative(double[] amounts, double[] years, double rate) {
        double sum = 0;
        for (int i = 0; i < amounts.length; i++) {
            sum += -years[i] * amounts[i] / Math.pow(1 + rate, years[i] + 1);
        }
        return sum;
    }
}
