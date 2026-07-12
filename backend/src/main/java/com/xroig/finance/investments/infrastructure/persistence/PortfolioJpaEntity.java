package com.xroig.finance.investments.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Persistence entity of the investments context, mapped to
 * {@code investments.portfolio} (owned by the V7 migration;
 * {@code ddl-auto=validate}). Kept separate from the pure
 * {@link com.xroig.finance.investments.domain.Portfolio} aggregate; a
 * {@link PortfolioJpaMapper} translates between the two.
 */
@Entity
@Table(name = "portfolio", schema = "investments")
public class PortfolioJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(name = "base_currency", nullable = false, length = 3)
    private String baseCurrency;

    public PortfolioJpaEntity() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getBaseCurrency() {
        return baseCurrency;
    }

    public void setBaseCurrency(String baseCurrency) {
        this.baseCurrency = baseCurrency;
    }
}
