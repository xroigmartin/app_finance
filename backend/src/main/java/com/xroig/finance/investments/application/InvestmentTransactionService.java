package com.xroig.finance.investments.application;

import com.xroig.finance.investments.application.port.CreateInvestmentTransaction;
import com.xroig.finance.investments.application.port.DeleteInvestmentTransaction;
import com.xroig.finance.investments.application.port.FindInvestmentTransactions;
import com.xroig.finance.investments.application.port.UpdateInvestmentTransaction;
import com.xroig.finance.investments.domain.CurrencyMoney;
import com.xroig.finance.investments.domain.InvestmentTransaction;
import com.xroig.finance.investments.domain.InvestmentTransactionId;
import com.xroig.finance.investments.domain.InvestmentTransactionRepository;
import com.xroig.finance.investments.domain.InvestmentTransactionType;
import com.xroig.finance.investments.domain.Portfolio;
import com.xroig.finance.investments.domain.PortfolioId;
import com.xroig.finance.investments.domain.PortfolioRepository;
import com.xroig.finance.investments.domain.Quantity;
import com.xroig.finance.investments.domain.Security;
import com.xroig.finance.investments.domain.SecurityId;
import com.xroig.finance.investments.domain.SecurityRepository;
import com.xroig.finance.shared.domain.NotFoundException;
import com.xroig.finance.shared.domain.ValidationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Application service for the manual operation use cases (RF-2): orchestrates the
 * aggregate and the outbound ports. The §3 sign invariants live in
 * {@link InvestmentTransaction} (violation → {@code ValidationException}, §8);
 * here we resolve identities, apply the RN-4 <b>hard</b> guard on manual sales
 * (beyond the held position at the date → 400, unlike the import's warning),
 * preserve the {@code external_id} on edits and serve the filtered listing.
 */
@Service
@Transactional
public class InvestmentTransactionService
        implements CreateInvestmentTransaction, UpdateInvestmentTransaction,
        DeleteInvestmentTransaction, FindInvestmentTransactions {

    private final PortfolioRepository portfolios;
    private final SecurityRepository securities;
    private final InvestmentTransactionRepository transactions;

    public InvestmentTransactionService(PortfolioRepository portfolios, SecurityRepository securities,
                                        InvestmentTransactionRepository transactions) {
        this.portfolios = portfolios;
        this.securities = securities;
        this.transactions = transactions;
    }

    @Override
    public InvestmentTransactionView create(long portfolioId, InvestmentTransactionCommand command) {
        Portfolio portfolio = requirePortfolio(portfolioId);
        Security security = resolveSecurity(command.securityId());
        InvestmentTransaction built = build(portfolio.id(), command, null).build();
        requireSufficientPositionOnSell(built, null);
        return toView(transactions.save(built), security);
    }

    @Override
    public InvestmentTransactionView update(long id, InvestmentTransactionCommand command) {
        InvestmentTransaction existing = transactions.findById(new InvestmentTransactionId(id))
                .orElseThrow(() -> new NotFoundException("Operación no encontrada"));
        Security security = resolveSecurity(command.securityId());
        InvestmentTransaction rebuilt = build(existing.portfolioId(), command, existing.externalId())
                .rehydrate(existing.id());
        requireSufficientPositionOnSell(rebuilt, existing.id());
        return toView(transactions.save(rebuilt), security);
    }

    @Override
    public void delete(long id) {
        transactions.deleteById(new InvestmentTransactionId(id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<InvestmentTransactionView> find(long portfolioId, TransactionFilter filter) {
        requirePortfolio(portfolioId);
        Map<Long, Security> catalog = securities.findAll().stream()
                .collect(Collectors.toMap(s -> s.id().value(), Function.identity()));
        return transactions.findByPortfolio(new PortfolioId(portfolioId)).stream()
                .filter(tx -> matches(tx, filter))
                .sorted(Comparator.comparing(InvestmentTransaction::tradeDate).reversed())
                .map(tx -> toView(tx, tx.securityId() == null ? null : catalog.get(tx.securityId().value())))
                .toList();
    }

    private static boolean matches(InvestmentTransaction tx, TransactionFilter filter) {
        return (filter.type() == null || tx.type() == filter.type())
                && (filter.from() == null || !tx.tradeDate().isBefore(filter.from()))
                && (filter.to() == null || !tx.tradeDate().isAfter(filter.to()))
                && (filter.securityId() == null || (tx.securityId() != null
                        && tx.securityId().value() == filter.securityId()));
    }

    /**
     * RN-4 (lado duro, manual): una venta no puede superar la cantidad en posición
     * a su fecha — las adquisiciones del mismo día cuentan, con la tolerancia de
     * precisión de {@link Quantity}. En edición, la propia fila editada se excluye.
     */
    private void requireSufficientPositionOnSell(InvestmentTransaction candidate,
                                                 InvestmentTransactionId excluded) {
        if (candidate.type() != InvestmentTransactionType.SELL) {
            return;
        }
        Quantity held = transactions.findByPortfolio(candidate.portfolioId()).stream()
                .filter(tx -> excluded == null || !excluded.equals(tx.id()))
                .filter(tx -> candidate.securityId().equals(tx.securityId()))
                .filter(tx -> tx.quantity() != null && !tx.tradeDate().isAfter(candidate.tradeDate()))
                .map(InvestmentTransaction::quantity)
                .reduce(Quantity.ZERO, Quantity::add);
        if (candidate.quantity().abs().exceeds(held)) {
            throw new ValidationException(
                    "Venta sin posición suficiente a " + candidate.tradeDate() + " (RN-4)");
        }
    }

    private InvestmentTransaction.Builder build(PortfolioId portfolioId,
                                                InvestmentTransactionCommand command, String externalId) {
        return InvestmentTransaction.builder()
                .portfolio(portfolioId)
                .security(command.securityId() == null ? null : new SecurityId(command.securityId()))
                .type(command.type())
                .tradeDate(command.tradeDate())
                .quantity(command.quantity() == null ? null : Quantity.of(command.quantity()))
                .price(command.price())
                .amount(money(command.amount(), command.currency(), command.currency()))
                .counterAmount(money(command.counterAmount(), command.counterCurrency(), null))
                .fee(money(command.fee(), command.feeCurrency(), command.currency()))
                .tax(money(command.tax(), command.taxCurrency(), command.currency()))
                .fxRateToBase(command.fxRateToBase())
                .description(command.description())
                .externalId(externalId);
    }

    private static CurrencyMoney money(BigDecimal amount, String currency, String fallbackCurrency) {
        if (amount == null) {
            return null;
        }
        String effective = currency == null || currency.isBlank() ? fallbackCurrency : currency;
        return CurrencyMoney.of(amount, effective);
    }

    private Portfolio requirePortfolio(long portfolioId) {
        return portfolios.findById(new PortfolioId(portfolioId))
                .orElseThrow(() -> new NotFoundException("Cartera no encontrada"));
    }

    private Security resolveSecurity(Long securityId) {
        if (securityId == null) {
            return null;
        }
        return securities.findById(new SecurityId(securityId))
                .orElseThrow(() -> new NotFoundException("Instrumento no encontrado"));
    }

    private static InvestmentTransactionView toView(InvestmentTransaction tx, Security security) {
        return new InvestmentTransactionView(
                tx.id() == null ? 0L : tx.id().value(),
                tx.type(), tx.tradeDate(),
                tx.securityId() == null ? null : tx.securityId().value(),
                security == null ? null : security.name(),
                tx.quantity() == null ? null : tx.quantity().value(),
                tx.price(),
                tx.amount().amount(), tx.amount().currency(),
                tx.counterAmount() == null ? null : tx.counterAmount().amount(),
                tx.counterAmount() == null ? null : tx.counterAmount().currency(),
                tx.fee() == null ? null : tx.fee().amount(),
                tx.fee() == null ? null : tx.fee().currency(),
                tx.tax() == null ? null : tx.tax().amount(),
                tx.tax() == null ? null : tx.tax().currency(),
                tx.fxRateToBase(), tx.description(), tx.externalId());
    }
}
