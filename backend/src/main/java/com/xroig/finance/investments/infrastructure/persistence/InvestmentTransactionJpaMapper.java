package com.xroig.finance.investments.infrastructure.persistence;

import com.xroig.finance.investments.domain.CurrencyMoney;
import com.xroig.finance.investments.domain.InvestmentTransaction;
import com.xroig.finance.investments.domain.InvestmentTransactionId;
import com.xroig.finance.investments.domain.InvestmentTransactionType;
import com.xroig.finance.investments.domain.PortfolioId;
import com.xroig.finance.investments.domain.Quantity;
import com.xroig.finance.investments.domain.SecurityId;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Translates between the pure {@link InvestmentTransaction} aggregate and its
 * {@link InvestmentTransactionJpaEntity}. Fee/tax follow the §3 storage rule for
 * their currency column: null means "the entry's currency" (the common case), a
 * value only when it differs (IBKR FX trades) — the domain always carries the
 * effective currency, so the mapper resolves the column on both directions.
 * Rehydration runs the aggregate's invariants again: a corrupted row fails fast.
 */
@Component
public class InvestmentTransactionJpaMapper {

    public InvestmentTransaction toDomain(InvestmentTransactionJpaEntity entity) {
        return InvestmentTransaction.builder()
                .portfolio(new PortfolioId(entity.getPortfolioId()))
                .security(entity.getSecurityId() == null ? null : new SecurityId(entity.getSecurityId()))
                .type(InvestmentTransactionType.valueOf(entity.getType()))
                .tradeDate(entity.getTradeDate())
                .quantity(entity.getQuantity() == null ? null : Quantity.of(entity.getQuantity()))
                .price(entity.getPrice())
                .amount(CurrencyMoney.of(entity.getAmount(), entity.getCurrency()))
                .counterAmount(money(entity.getCounterAmount(), entity.getCounterCurrency(), null))
                .fee(money(entity.getFee(), entity.getFeeCurrency(), entity.getCurrency()))
                .tax(money(entity.getTax(), entity.getTaxCurrency(), entity.getCurrency()))
                .fxRateToBase(entity.getFxRateToBase())
                .description(entity.getDescription())
                .externalId(entity.getExternalId())
                .rehydrate(new InvestmentTransactionId(entity.getId()));
    }

    public InvestmentTransactionJpaEntity toJpa(InvestmentTransaction transaction) {
        InvestmentTransactionJpaEntity entity = new InvestmentTransactionJpaEntity();
        if (transaction.id() != null) {
            entity.setId(transaction.id().value());
        }
        entity.setPortfolioId(transaction.portfolioId().value());
        entity.setSecurityId(transaction.securityId() == null ? null : transaction.securityId().value());
        entity.setType(transaction.type().name());
        entity.setTradeDate(transaction.tradeDate());
        entity.setQuantity(transaction.quantity() == null ? null : transaction.quantity().value());
        entity.setPrice(transaction.price());
        entity.setAmount(transaction.amount().amount());
        entity.setCurrency(transaction.currency());
        if (transaction.counterAmount() != null) {
            entity.setCounterAmount(transaction.counterAmount().amount());
            entity.setCounterCurrency(transaction.counterAmount().currency());
        }
        if (transaction.fee() != null) {
            entity.setFee(transaction.fee().amount());
            entity.setFeeCurrency(ownCurrencyColumn(transaction.fee(), transaction.currency()));
        }
        if (transaction.tax() != null) {
            entity.setTax(transaction.tax().amount());
            entity.setTaxCurrency(ownCurrencyColumn(transaction.tax(), transaction.currency()));
        }
        entity.setFxRateToBase(transaction.fxRateToBase());
        entity.setDescription(transaction.description());
        entity.setExternalId(transaction.externalId());
        return entity;
    }

    /** Effective currency of a charge read back: its own column or, when null, the entry's. */
    private CurrencyMoney money(BigDecimal amount, String ownCurrency, String entryCurrency) {
        if (amount == null) {
            return null;
        }
        return CurrencyMoney.of(amount, ownCurrency != null ? ownCurrency : entryCurrency);
    }

    /** §3: the charge's currency column is null when it matches the entry's. */
    private String ownCurrencyColumn(CurrencyMoney charge, String entryCurrency) {
        return charge.currency().equals(entryCurrency) ? null : charge.currency();
    }
}
