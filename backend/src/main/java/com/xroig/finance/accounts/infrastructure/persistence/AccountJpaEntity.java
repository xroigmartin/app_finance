package com.xroig.finance.accounts.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/**
 * Persistence entity for the accounts context, mapped to the existing {@code accounts}
 * table (owned by Flyway; {@code ddl-auto=validate}). Kept separate from the pure
 * {@link com.xroig.finance.accounts.domain.Account} aggregate; an {@link AccountJpaMapper}
 * translates between the two.
 *
 * <p>During the incremental migration this maps the same table as the legacy
 * {@code model.Account} entity (still referenced by not-yet-migrated contexts);
 * both validate against the identical schema.
 */
@Entity
@Table(name = "accounts")
public class AccountJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String type;

    @Column(name = "initial_balance", nullable = false)
    private BigDecimal initialBalance;

    protected AccountJpaEntity() {
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

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public BigDecimal getInitialBalance() {
        return initialBalance;
    }

    public void setInitialBalance(BigDecimal initialBalance) {
        this.initialBalance = initialBalance;
    }
}
