package com.xroig.finance.categories.infrastructure.persistence;

import com.xroig.finance.accounts.infrastructure.persistence.AccountJpaEntity;
import com.xroig.finance.model.TransactionType;
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

/**
 * Persistence entity for the categories context, mapped to the existing {@code
 * categories} table (Flyway-owned; {@code ddl-auto=validate}). Kept separate from the
 * pure {@link com.xroig.finance.categories.domain.Category} aggregate.
 *
 * <p>The owning account and parent are mapped as {@code @ManyToOne} associations on
 * the {@code account_id}/{@code parent_id} columns. The aggregate references them only
 * by id, so the mapper sets these from {@code getReferenceById} on write and reads
 * {@code getId()} on read; the read adapter navigates them (eagerly) to assemble the
 * nested {@code CategoryView}. During the migration this coexists with the legacy
 * {@code model.Category} on the same table.
 */
@Entity
@Table(name = "categories")
public class CategoryJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionType type;

    @Column(nullable = false)
    private String color;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "account_id")
    private AccountJpaEntity account;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "parent_id")
    private CategoryJpaEntity parent;

    protected CategoryJpaEntity() {
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

    public TransactionType getType() {
        return type;
    }

    public void setType(TransactionType type) {
        this.type = type;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public AccountJpaEntity getAccount() {
        return account;
    }

    public void setAccount(AccountJpaEntity account) {
        this.account = account;
    }

    public CategoryJpaEntity getParent() {
        return parent;
    }

    public void setParent(CategoryJpaEntity parent) {
        this.parent = parent;
    }
}
