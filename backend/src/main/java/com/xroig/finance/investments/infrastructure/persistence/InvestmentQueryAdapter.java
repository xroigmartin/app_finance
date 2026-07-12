package com.xroig.finance.investments.infrastructure.persistence;

import com.xroig.finance.investments.application.InvestmentQueryPort;
import com.xroig.finance.investments.application.InvestmentsSummaryView;
import com.xroig.finance.investments.application.InvestmentsSummaryView.PortfolioValueView;
import com.xroig.finance.investments.application.PortfolioSummaryView;
import com.xroig.finance.investments.application.PositionView;
import com.xroig.finance.investments.application.ValuationHistoryView;
import com.xroig.finance.investments.domain.CurrencyConverter;
import com.xroig.finance.investments.domain.CurrencyMoney;
import com.xroig.finance.investments.domain.ExchangeRate;
import com.xroig.finance.investments.domain.ExchangeRateRepository;
import com.xroig.finance.investments.domain.InvestmentTransaction;
import com.xroig.finance.investments.domain.InvestmentTransactionRepository;
import com.xroig.finance.investments.domain.InvestmentTransactionType;
import com.xroig.finance.investments.domain.Portfolio;
import com.xroig.finance.investments.domain.PortfolioId;
import com.xroig.finance.investments.domain.PortfolioPositions;
import com.xroig.finance.investments.domain.PortfolioRepository;
import com.xroig.finance.investments.domain.Position;
import com.xroig.finance.investments.domain.PositionCalculator;
import com.xroig.finance.investments.domain.Quantity;
import com.xroig.finance.investments.domain.Security;
import com.xroig.finance.investments.domain.SecurityId;
import com.xroig.finance.investments.domain.SecurityRepository;
import com.xroig.finance.shared.domain.NotFoundException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Read-side adapter (CQRS) for the investments context: assembles the
 * {@link InvestmentQueryPort} views from the persisted graph without
 * reconstructing aggregates to write. Positions and cash come from the domain's
 * {@link PositionCalculator} (RN-2/RN-3); each position is valued at the latest
 * quote ≤ the valuation date (RN-6 — at cost with a notice when none exists) and
 * converted to the base currency with the RN-7 dual mechanism: amounts fixed in
 * the past (contributions, dividends, cost basis) with their own snapshot
 * ({@code fx_rate_to_base}, RN-7a), valuation at a date with the rate table via
 * the EUR pivot (RN-7b), degrading to 1:1 when no rate exists (the calculator
 * already flags it). Nothing is materialized: every figure is recomputed per
 * request from {@code investment_transaction} + {@code price_quote} +
 * {@code exchange_rate} (§3).
 */
@Component
public class InvestmentQueryAdapter implements InvestmentQueryPort {

    private static final String EUR = ExchangeRate.PIVOT;
    private static final int PERCENT_SCALE = 2;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final PortfolioRepository portfolios;
    private final SecurityRepository securities;
    private final InvestmentTransactionRepository transactions;
    private final ExchangeRateRepository exchangeRates;
    private final PriceQuoteJpaRepository quotes;
    private final PositionCalculator calculator = new PositionCalculator();

    public InvestmentQueryAdapter(PortfolioRepository portfolios, SecurityRepository securities,
                                  InvestmentTransactionRepository transactions,
                                  ExchangeRateRepository exchangeRates, PriceQuoteJpaRepository quotes) {
        this.portfolios = portfolios;
        this.securities = securities;
        this.transactions = transactions;
        this.exchangeRates = exchangeRates;
        this.quotes = quotes;
    }

    @Override
    public List<PositionView> positions(long portfolioId) {
        Context ctx = load(requirePortfolio(portfolioId), converter());
        Valuation valuation = valueAt(ctx, ctx.transactions(), LocalDate.now());
        return valuation.positions().stream()
                .map(valued -> toView(valued, ctx, valuation.total()))
                .toList();
    }

    @Override
    public PortfolioSummaryView summary(long portfolioId) {
        Portfolio portfolio = requirePortfolio(portfolioId);
        Context ctx = load(portfolio, converter());
        LocalDate today = LocalDate.now();
        String base = portfolio.baseCurrency();
        Valuation valuation = valueAt(ctx, ctx.transactions(), today);

        CurrencyMoney contributions = CurrencyMoney.zero(base);
        CurrencyMoney dividends = CurrencyMoney.zero(base);
        for (InvestmentTransaction tx : ctx.transactions()) {
            if (isExternalFlow(tx)) {
                contributions = contributions.add(fixedToBase(tx.amount(), tx, base, ctx.converter()));
            }
            if (tx.type() == InvestmentTransactionType.DIVIDEND && tx.tradeDate().getYear() == today.getYear()) {
                dividends = dividends.add(fixedToBase(tx.amount(), tx, base, ctx.converter()));
            }
        }

        CurrencyMoney latent = CurrencyMoney.zero(base);
        CurrencyMoney capitalizedCost = CurrencyMoney.zero(base);
        for (ValuedPosition valued : valuation.positions()) {
            latent = latent.add(latentPnl(valued, base));
            capitalizedCost = capitalizedCost.add(valued.position().costBasis());
        }

        Map<String, BigDecimal> cash = valuation.cash().entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().amount(),
                        (a, b) -> a, LinkedHashMap::new));

        return new PortfolioSummaryView(portfolioId, portfolio.name(), base,
                valuation.total().amount(), valuation.valuationDate(),
                contributions.amount(), latent.amount(), percentOver(latent, capitalizedCost),
                cash, dividends.amount());
    }

    @Override
    public List<ValuationHistoryView> valuationHistory(long portfolioId) {
        Context ctx = load(requirePortfolio(portfolioId), converter());
        LocalDate today = LocalDate.now();
        String base = ctx.portfolio().baseCurrency();

        TreeSet<LocalDate> dates = new TreeSet<>();
        for (InvestmentTransaction tx : ctx.transactions()) {
            if (isExternalFlow(tx)) {
                dates.add(tx.tradeDate());
            }
        }
        ctx.quoteSeries().values().forEach(series -> dates.addAll(series.headMap(today, true).keySet()));

        List<ValuationHistoryView> history = new ArrayList<>();
        for (LocalDate date : dates) {
            List<InvestmentTransaction> upTo = ctx.transactions().stream()
                    .filter(tx -> !tx.tradeDate().isAfter(date))
                    .toList();
            Valuation valuation = valueAt(ctx, upTo, date);
            CurrencyMoney contributed = CurrencyMoney.zero(base);
            for (InvestmentTransaction tx : upTo) {
                if (isExternalFlow(tx)) {
                    contributed = contributed.add(fixedToBase(tx.amount(), tx, base, ctx.converter()));
                }
            }
            history.add(new ValuationHistoryView(date, valuation.total().amount(), contributed.amount()));
        }
        return history;
    }

    @Override
    public InvestmentsSummaryView globalSummary() {
        CurrencyConverter converter = converter();
        LocalDate today = LocalDate.now();
        CurrencyMoney total = CurrencyMoney.zero(EUR);
        LocalDate oldestDate = null;
        List<PortfolioValueView> views = new ArrayList<>();
        for (Portfolio portfolio : portfolios.findAll()) {
            Context ctx = load(portfolio, converter);
            Valuation valuation = valueAt(ctx, ctx.transactions(), today);
            CurrencyMoney valueEur = valueToCurrency(valuation.total(), EUR, today, converter);
            views.add(new PortfolioValueView(portfolio.id().value(), portfolio.name(),
                    portfolio.baseCurrency(), valueEur.amount(), valuation.valuationDate()));
            total = total.add(valueEur);
            oldestDate = oldest(oldestDate, valuation.valuationDate());
        }
        return new InvestmentsSummaryView(total.amount(), oldestDate, views);
    }

    // ---- valuation core ----

    /** Everything a valuation needs, loaded once per request. */
    private record Context(Portfolio portfolio,
                           List<InvestmentTransaction> transactions,
                           CurrencyConverter converter,
                           Map<Long, Security> securities,
                           Map<Long, NavigableMap<LocalDate, BigDecimal>> quoteSeries) {

        private Security security(SecurityId id) {
            return securities.get(id.value());
        }
    }

    /** One open position valued at a date (market price in the security's currency, value in base). */
    private record ValuedPosition(Position position, BigDecimal marketPrice, LocalDate quoteDate,
                                  CurrencyMoney marketValue, boolean pricedAtCost) {
    }

    /** A portfolio valued at a date: open positions, cash per currency and total in base. */
    private record Valuation(List<ValuedPosition> positions, Map<String, CurrencyMoney> cash,
                             CurrencyMoney total, LocalDate valuationDate) {
    }

    private Context load(Portfolio portfolio, CurrencyConverter converter) {
        List<InvestmentTransaction> txs = transactions.findByPortfolio(portfolio.id());
        Set<Long> securityIds = txs.stream()
                .map(InvestmentTransaction::securityId)
                .filter(id -> id != null)
                .map(SecurityId::value)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, Security> securitiesById = new LinkedHashMap<>();
        for (Long id : securityIds) {
            securities.findById(new SecurityId(id)).ifPresent(s -> securitiesById.put(id, s));
        }
        Map<Long, NavigableMap<LocalDate, BigDecimal>> series = new LinkedHashMap<>();
        if (!securityIds.isEmpty()) {
            for (PriceQuoteJpaEntity quote : quotes.findBySecurityIdIn(securityIds)) {
                series.computeIfAbsent(quote.getSecurityId(), id -> new TreeMap<>())
                        .put(quote.getQuoteDate(), quote.getPrice());
            }
        }
        return new Context(portfolio, txs, converter, securitiesById, series);
    }

    /**
     * Values the portfolio built from {@code txs} at {@code asOf}: open positions at
     * the latest quote ≤ the date (RN-6, at cost without one), cash per currency, and
     * the total in base converted with the rate table at the date (RN-7b). The
     * valuation date is the oldest quote date used.
     */
    private Valuation valueAt(Context ctx, List<InvestmentTransaction> txs, LocalDate asOf) {
        String base = ctx.portfolio().baseCurrency();
        PortfolioPositions computed = calculator.calculate(base, txs, ctx.converter());

        List<ValuedPosition> valued = new ArrayList<>();
        CurrencyMoney total = CurrencyMoney.zero(base);
        LocalDate valuationDate = null;
        for (Position position : computed.positions()) {
            if (!position.quantity().abs().exceeds(Quantity.ZERO)) {
                continue; // posición cerrada: solo cuenta su P&L realizado, no se lista
            }
            ValuedPosition vp = value(position, ctx, asOf);
            if (!vp.pricedAtCost()) {
                valuationDate = oldest(valuationDate, vp.quoteDate());
            }
            total = total.add(vp.marketValue());
            valued.add(vp);
        }
        for (CurrencyMoney cash : computed.cashByCurrency().values()) {
            total = total.add(valueToCurrency(cash, base, asOf, ctx.converter()));
        }
        return new Valuation(valued, computed.cashByCurrency(), total, valuationDate);
    }

    private ValuedPosition value(Position position, Context ctx, LocalDate asOf) {
        NavigableMap<LocalDate, BigDecimal> series = ctx.quoteSeries().get(position.securityId().value());
        Map.Entry<LocalDate, BigDecimal> quote = series == null ? null : series.floorEntry(asOf);
        if (quote == null) {
            return new ValuedPosition(position, null, null, position.costBasis(), true);
        }
        Security security = ctx.security(position.securityId());
        CurrencyMoney raw = CurrencyMoney.of(
                position.quantity().value().multiply(quote.getValue()), security.currency());
        CurrencyMoney value = valueToCurrency(raw, ctx.portfolio().baseCurrency(), asOf, ctx.converter());
        return new ValuedPosition(position, quote.getValue(), quote.getKey(), value, false);
    }

    private PositionView toView(ValuedPosition valued, Context ctx, CurrencyMoney total) {
        Position position = valued.position();
        Security security = ctx.security(position.securityId());
        String base = ctx.portfolio().baseCurrency();
        CurrencyMoney latent = latentPnl(valued, base);
        CurrencyMoney averageCost = position.averageCost();
        BigDecimal weight = total.isPositive()
                ? valued.marketValue().amount().multiply(HUNDRED)
                        .divide(total.amount(), PERCENT_SCALE, RoundingMode.HALF_UP)
                : null;
        return new PositionView(security.id().value(), security.isin(), security.name(),
                security.ticker(), security.currency(), position.quantity().value(),
                averageCost == null ? null : averageCost.amount(), position.costBasis().amount(),
                valued.marketPrice(), valued.quoteDate(), valued.marketValue().amount(),
                latent.amount(), percentOver(latent, position.costBasis()), weight,
                valued.pricedAtCost());
    }

    /** At cost there is no latent P&L (RN-6): the value *is* the cost. */
    private CurrencyMoney latentPnl(ValuedPosition valued, String base) {
        return valued.pricedAtCost()
                ? CurrencyMoney.zero(base)
                : valued.marketValue().subtract(valued.position().costBasis());
    }

    // ---- conversion (RN-7) ----

    private CurrencyConverter converter() {
        return new CurrencyConverter(exchangeRates.findAll());
    }

    /**
     * Converts a fixed-in-the-past amount to base with the entry's own snapshot
     * (RN-7a); manual entries fall back to the rate table at the trade date (RN-7b)
     * and, as a last resort, 1:1 (the same degradation the calculator applies).
     */
    private CurrencyMoney fixedToBase(CurrencyMoney value, InvestmentTransaction tx,
                                      String base, CurrencyConverter converter) {
        if (value.currency().equals(base)) {
            return value;
        }
        if (tx.fxRateToBase() != null && value.currency().equals(tx.currency())) {
            return CurrencyMoney.of(value.amount().multiply(tx.fxRateToBase()), base);
        }
        return converter.convert(value, base, tx.tradeDate())
                .orElseGet(() -> CurrencyMoney.of(value.amount(), base));
    }

    /** Valuation-at-a-date conversion (RN-7b), degrading to 1:1 when no rate exists. */
    private CurrencyMoney valueToCurrency(CurrencyMoney value, String target, LocalDate date,
                                          CurrencyConverter converter) {
        if (value.currency().equals(target)) {
            return value;
        }
        return converter.convert(value, target, date)
                .orElseGet(() -> CurrencyMoney.of(value.amount(), target));
    }

    // ---- small helpers ----

    private Portfolio requirePortfolio(long portfolioId) {
        return portfolios.findById(new PortfolioId(portfolioId))
                .orElseThrow(() -> new NotFoundException("Cartera no encontrada"));
    }

    private static boolean isExternalFlow(InvestmentTransaction tx) {
        return tx.type() == InvestmentTransactionType.DEPOSIT
                || tx.type() == InvestmentTransactionType.WITHDRAWAL;
    }

    /** Percentage of {@code part} over {@code base} (scale 2), or null when the base is not positive. */
    private static BigDecimal percentOver(CurrencyMoney part, CurrencyMoney base) {
        if (!base.isPositive()) {
            return null;
        }
        return part.amount().multiply(HUNDRED)
                .divide(base.amount(), PERCENT_SCALE, RoundingMode.HALF_UP);
    }

    private static LocalDate oldest(LocalDate current, LocalDate candidate) {
        if (candidate == null) {
            return current;
        }
        return current == null || candidate.isBefore(current) ? candidate : current;
    }
}
