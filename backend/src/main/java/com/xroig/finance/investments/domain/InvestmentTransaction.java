package com.xroig.finance.investments.domain;

import com.xroig.finance.investments.domain.InvestmentTransactionType.AmountRule;
import com.xroig.finance.investments.domain.InvestmentTransactionType.QuantityRule;
import com.xroig.finance.investments.domain.InvestmentTransactionType.SecurityRule;
import com.xroig.finance.shared.domain.ValidationException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/**
 * Investment operation aggregate root (§3). Every amount is stored with the sign
 * of the real cash flow (the Flex convention), so the portfolio cash (RN-2) and
 * the positions (RN-3) are direct sums without per-type sign logic; the invariants
 * of each {@link InvestmentTransactionType} are enforced here on construction.
 *
 * <p>Conditional fields: the security is null on pure cash operations; the counter
 * leg ({@code counterAmount}) exists only on {@code FX_TRADE} (incoming, positive,
 * another currency); fee/tax carry their own currency when it differs from the
 * entry's (IBKR FX trades). {@code fxRateToBase} is the Flex's snapshot of the
 * applied rate (RN-7a) — null on manual entries, which fall back to the
 * {@code exchange_rate} table (RN-7b). {@code externalId} is the import
 * idempotency key (RN-10) — null on manual entries.
 */
public class InvestmentTransaction {

    private static final int RATE_SCALE = 8;

    private final InvestmentTransactionId id;
    private final PortfolioId portfolioId;
    private final SecurityId securityId;
    private final InvestmentTransactionType type;
    private final LocalDate tradeDate;
    private final Quantity quantity;
    private final BigDecimal price;
    private final CurrencyMoney amount;
    private final CurrencyMoney counterAmount;
    private final CurrencyMoney fee;
    private final CurrencyMoney tax;
    private final BigDecimal fxRateToBase;
    private final String description;
    private final String externalId;

    private InvestmentTransaction(InvestmentTransactionId id, Builder builder) {
        this.id = id;
        this.portfolioId = require(builder.portfolioId, "La operación requiere una cartera");
        this.type = require(builder.type, "La operación requiere un tipo");
        this.tradeDate = require(builder.tradeDate, "La operación requiere una fecha");
        this.amount = require(builder.amount, "La operación requiere un importe con divisa");
        this.securityId = requireSecurityPerType(builder.securityId);
        this.quantity = requireQuantityPerType(builder.quantity);
        requireAmountSignPerType();
        this.counterAmount = requireCounterLegPerType(builder.counterAmount);
        this.fee = requireCashFlowSign(builder.fee, "La comisión");
        this.tax = requireCashFlowSign(builder.tax, "La retención");
        this.price = requirePositiveRate(builder.price, "El precio");
        this.fxRateToBase = requirePositiveRate(builder.fxRateToBase, "El tipo de cambio del apunte");
        this.description = builder.description;
        this.externalId = normalizeExternalId(builder.externalId);
    }

    public static Builder builder() {
        return new Builder();
    }

    public InvestmentTransactionId id() {
        return id;
    }

    public PortfolioId portfolioId() {
        return portfolioId;
    }

    public SecurityId securityId() {
        return securityId;
    }

    public InvestmentTransactionType type() {
        return type;
    }

    public LocalDate tradeDate() {
        return tradeDate;
    }

    public Quantity quantity() {
        return quantity;
    }

    public BigDecimal price() {
        return price;
    }

    public CurrencyMoney amount() {
        return amount;
    }

    /** Currency of the entry (the amount's). */
    public String currency() {
        return amount.currency();
    }

    public CurrencyMoney counterAmount() {
        return counterAmount;
    }

    public CurrencyMoney fee() {
        return fee;
    }

    public CurrencyMoney tax() {
        return tax;
    }

    public BigDecimal fxRateToBase() {
        return fxRateToBase;
    }

    public String description() {
        return description;
    }

    public String externalId() {
        return externalId;
    }

    private SecurityId requireSecurityPerType(SecurityId securityId) {
        SecurityRule rule = type.securityRule();
        if (rule == SecurityRule.REQUIRED && securityId == null) {
            throw new ValidationException("Una operación " + type + " requiere un instrumento");
        }
        if (rule == SecurityRule.FORBIDDEN && securityId != null) {
            throw new ValidationException("Una operación " + type + " es de efectivo puro: no admite instrumento");
        }
        return securityId;
    }

    private Quantity requireQuantityPerType(Quantity quantity) {
        QuantityRule rule = type.quantityRule();
        if (rule == QuantityRule.NONE) {
            if (quantity != null) {
                throw new ValidationException("Una operación " + type + " no lleva cantidad de títulos");
            }
            return null;
        }
        if (quantity == null) {
            throw new ValidationException("Una operación " + type + " requiere una cantidad de títulos");
        }
        boolean valid = switch (rule) {
            case POSITIVE -> quantity.isPositive();
            case NEGATIVE -> quantity.isNegative();
            case NON_ZERO -> !quantity.isZero();
            case NONE -> true;
        };
        if (!valid) {
            throw new ValidationException(
                    "La cantidad de una operación " + type + " viola el convenio de signos (§3)");
        }
        return quantity;
    }

    private void requireAmountSignPerType() {
        boolean valid = switch (type.amountRule()) {
            case POSITIVE -> amount.isPositive();
            case NEGATIVE -> amount.isNegative();
            case ZERO -> amount.isZero();
        };
        if (!valid) {
            throw new ValidationException(
                    "El importe de una operación " + type + " viola el convenio de signos (§3)");
        }
    }

    private CurrencyMoney requireCounterLegPerType(CurrencyMoney counterAmount) {
        if (type != InvestmentTransactionType.FX_TRADE) {
            if (counterAmount != null) {
                throw new ValidationException("Solo una conversión FX_TRADE lleva pierna entrante");
            }
            return null;
        }
        if (counterAmount == null || !counterAmount.isPositive()) {
            throw new ValidationException(
                    "Una conversión FX_TRADE requiere una pierna entrante positiva");
        }
        if (counterAmount.currency().equals(amount.currency())) {
            throw new ValidationException(
                    "Las dos piernas de una conversión FX_TRADE deben ser de divisas distintas");
        }
        return counterAmount;
    }

    private static CurrencyMoney requireCashFlowSign(CurrencyMoney charge, String subject) {
        if (charge != null && !charge.isNegative()) {
            throw new ValidationException(subject + " lleva el signo del flujo de caja (negativa)");
        }
        return charge;
    }

    private static BigDecimal requirePositiveRate(BigDecimal value, String subject) {
        if (value == null) {
            return null;
        }
        if (value.signum() <= 0) {
            throw new ValidationException(subject + " debe ser positivo");
        }
        return value.setScale(RATE_SCALE, RoundingMode.HALF_UP);
    }

    private static String normalizeExternalId(String externalId) {
        if (externalId == null || externalId.isBlank()) {
            return null;
        }
        return externalId.trim();
    }

    private static <T> T require(T value, String message) {
        if (value == null) {
            throw new ValidationException(message);
        }
        return value;
    }

    /** Fluent builder: the operation has many optional, type-conditional fields. */
    public static final class Builder {

        private PortfolioId portfolioId;
        private SecurityId securityId;
        private InvestmentTransactionType type;
        private LocalDate tradeDate;
        private Quantity quantity;
        private BigDecimal price;
        private CurrencyMoney amount;
        private CurrencyMoney counterAmount;
        private CurrencyMoney fee;
        private CurrencyMoney tax;
        private BigDecimal fxRateToBase;
        private String description;
        private String externalId;

        private Builder() {
        }

        public Builder portfolio(PortfolioId portfolioId) {
            this.portfolioId = portfolioId;
            return this;
        }

        public Builder security(SecurityId securityId) {
            this.securityId = securityId;
            return this;
        }

        public Builder type(InvestmentTransactionType type) {
            this.type = type;
            return this;
        }

        public Builder tradeDate(LocalDate tradeDate) {
            this.tradeDate = tradeDate;
            return this;
        }

        public Builder quantity(Quantity quantity) {
            this.quantity = quantity;
            return this;
        }

        public Builder price(BigDecimal price) {
            this.price = price;
            return this;
        }

        public Builder price(String price) {
            return price(price == null ? null : new BigDecimal(price));
        }

        public Builder amount(CurrencyMoney amount) {
            this.amount = amount;
            return this;
        }

        public Builder counterAmount(CurrencyMoney counterAmount) {
            this.counterAmount = counterAmount;
            return this;
        }

        public Builder fee(CurrencyMoney fee) {
            this.fee = fee;
            return this;
        }

        public Builder tax(CurrencyMoney tax) {
            this.tax = tax;
            return this;
        }

        public Builder fxRateToBase(BigDecimal fxRateToBase) {
            this.fxRateToBase = fxRateToBase;
            return this;
        }

        public Builder fxRateToBase(String fxRateToBase) {
            return fxRateToBase(fxRateToBase == null ? null : new BigDecimal(fxRateToBase));
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder externalId(String externalId) {
            this.externalId = externalId;
            return this;
        }

        /** Builds a new operation (identity assigned on persist), checking every invariant. */
        public InvestmentTransaction build() {
            return new InvestmentTransaction(null, this);
        }

        /** Rebuilds a persisted operation (identity present), from the persistence adapter. */
        public InvestmentTransaction rehydrate(InvestmentTransactionId id) {
            if (id == null) {
                throw new IllegalArgumentException("Una operación rehidratada necesita identidad");
            }
            return new InvestmentTransaction(id, this);
        }
    }
}
