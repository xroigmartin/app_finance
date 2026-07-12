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
 * {@code investments.price_quote} (owned by the V7 migration; the natural key is
 * backed by its {@code UNIQUE (security_id, quote_date)} constraint — the upsert
 * contract RN-9 lives in {@link PriceQuotePersistenceAdapter}).
 */
@Entity
@Table(name = "price_quote", schema = "investments")
public class PriceQuoteJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "security_id", nullable = false)
    private Long securityId;

    @Column(name = "quote_date", nullable = false)
    private LocalDate quoteDate;

    @Column(nullable = false, precision = 19, scale = 8)
    private BigDecimal price;

    public PriceQuoteJpaEntity() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getSecurityId() {
        return securityId;
    }

    public void setSecurityId(Long securityId) {
        this.securityId = securityId;
    }

    public LocalDate getQuoteDate() {
        return quoteDate;
    }

    public void setQuoteDate(LocalDate quoteDate) {
        this.quoteDate = quoteDate;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }
}
