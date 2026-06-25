package com.xroig.finance.transactions.infrastructure.persistence;

import com.xroig.finance.accounts.infrastructure.persistence.AccountJpaEntity;
import com.xroig.finance.categories.infrastructure.persistence.CategoryJpaEntity;
import com.xroig.finance.shared.domain.TransactionType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * Persistence entity for the transactions context, mapped to the existing {@code
 * transactions} table (Flyway-owned; {@code ddl-auto=validate}). Kept separate from
 * the pure {@link com.xroig.finance.transactions.domain.Transaction} aggregate. The
 * account/category/refundOf are {@code @ManyToOne} associations resolved by id via
 * {@code getReferenceById} on write and read back as ids; the read adapter navigates
 * them. During the migration this coexists with the legacy {@code model.Transaction}
 * on the same table.
 */
@Entity
@Table(name = "transactions")
public class TransactionJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDate date;

    @Column(nullable = false)
    private BigDecimal amount;

    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionType type;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "account_id")
    private AccountJpaEntity account;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "category_id")
    private CategoryJpaEntity category;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "refund_of_id")
    private TransactionJpaEntity refundOf;

    protected TransactionJpaEntity() {
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

    public TransactionType getType() {
        return type;
    }

    public void setType(TransactionType type) {
        this.type = type;
    }

    public AccountJpaEntity getAccount() {
        return account;
    }

    public void setAccount(AccountJpaEntity account) {
        this.account = account;
    }

    public CategoryJpaEntity getCategory() {
        return category;
    }

    public void setCategory(CategoryJpaEntity category) {
        this.category = category;
    }

    public TransactionJpaEntity getRefundOf() {
        return refundOf;
    }

    public void setRefundOf(TransactionJpaEntity refundOf) {
        this.refundOf = refundOf;
    }
}
