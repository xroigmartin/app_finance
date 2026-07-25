package com.xroig.finance.transfers.infrastructure.persistence;

import com.xroig.finance.accounts.infrastructure.persistence.AccountJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Persistence entity for the transfers context, mapped to the existing {@code
 * transfers} table (Flyway-owned; {@code ddl-auto=validate}). Kept separate from the
 * pure {@link com.xroig.finance.transfers.domain.Transfer} aggregate; the two ends are
 * {@code @ManyToOne} associations resolved by id via {@code getReferenceById} on write
 * and read back as ids. Coexists with the legacy {@code model.Transfer} on the same table.
 */
@Entity
@Table(name = "transfers")
public class TransferJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDate date;

    @Column(nullable = false)
    private BigDecimal amount;

    private String description;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "from_account_id")
    private AccountJpaEntity fromAccount;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "to_account_id")
    private AccountJpaEntity toAccount;

    public TransferJpaEntity() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public AccountJpaEntity getFromAccount() {
        return fromAccount;
    }

    public void setFromAccount(AccountJpaEntity fromAccount) {
        this.fromAccount = fromAccount;
    }

    public AccountJpaEntity getToAccount() {
        return toAccount;
    }

    public void setToAccount(AccountJpaEntity toAccount) {
        this.toAccount = toAccount;
    }
}
