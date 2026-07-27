package com.xroig.finance.investments.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Persistence entity of the investments context, mapped to
 * {@code investments.investment_transaction} (owned by the V7 migration; the
 * import idempotency RN-10 is backed by its {@code UNIQUE (portfolio_id,
 * external_id)} constraint). References to portfolio/security are plain id
 * columns — the aggregates reference each other by id and the schema is new, so
 * no {@code @ManyToOne} association is needed; the FKs live in the DDL. A
 * {@link InvestmentTransactionJpaMapper} translates to the pure
 * {@link com.xroig.finance.investments.domain.InvestmentTransaction} aggregate.
 */
@Entity
@Table(name = "investment_transaction", schema = "investments")
public class InvestmentTransactionJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "portfolio_id", nullable = false)
    private Long portfolioId;

    @Column(name = "security_id")
    private Long securityId;

    @Column(nullable = false)
    private String type;

    @Column(name = "trade_date", nullable = false)
    private LocalDate tradeDate;

    @Column(precision = 19, scale = 8)
    private BigDecimal quantity;

    @Column(precision = 19, scale = 8)
    private BigDecimal price;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "counter_amount", precision = 19, scale = 4)
    private BigDecimal counterAmount;

    @Column(name = "counter_currency", length = 3)
    private String counterCurrency;

    @Column(precision = 19, scale = 4)
    private BigDecimal fee;

    @Column(name = "fee_currency", length = 3)
    private String feeCurrency;

    @Column(precision = 19, scale = 4)
    private BigDecimal tax;

    @Column(name = "tax_currency", length = 3)
    private String taxCurrency;

    @Column(name = "fx_rate_to_base", precision = 19, scale = 8)
    private BigDecimal fxRateToBase;

    private String description;

    @Column(name = "external_id")
    private String externalId;

    public InvestmentTransactionJpaEntity() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getPortfolioId() {
        return portfolioId;
    }

    public void setPortfolioId(Long portfolioId) {
        this.portfolioId = portfolioId;
    }

    public Long getSecurityId() {
        return securityId;
    }

    public void setSecurityId(Long securityId) {
        this.securityId = securityId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public LocalDate getTradeDate() {
        return tradeDate;
    }

    public void setTradeDate(LocalDate tradeDate) {
        this.tradeDate = tradeDate;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public BigDecimal getCounterAmount() {
        return counterAmount;
    }

    public void setCounterAmount(BigDecimal counterAmount) {
        this.counterAmount = counterAmount;
    }

    public String getCounterCurrency() {
        return counterCurrency;
    }

    public void setCounterCurrency(String counterCurrency) {
        this.counterCurrency = counterCurrency;
    }

    public BigDecimal getFee() {
        return fee;
    }

    public void setFee(BigDecimal fee) {
        this.fee = fee;
    }

    public String getFeeCurrency() {
        return feeCurrency;
    }

    public void setFeeCurrency(String feeCurrency) {
        this.feeCurrency = feeCurrency;
    }

    public BigDecimal getTax() {
        return tax;
    }

    public void setTax(BigDecimal tax) {
        this.tax = tax;
    }

    public String getTaxCurrency() {
        return taxCurrency;
    }

    public void setTaxCurrency(String taxCurrency) {
        this.taxCurrency = taxCurrency;
    }

    public BigDecimal getFxRateToBase() {
        return fxRateToBase;
    }

    public void setFxRateToBase(BigDecimal fxRateToBase) {
        this.fxRateToBase = fxRateToBase;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getExternalId() {
        return externalId;
    }

    public void setExternalId(String externalId) {
        this.externalId = externalId;
    }
}
