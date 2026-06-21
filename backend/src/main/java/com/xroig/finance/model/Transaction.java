package com.xroig.finance.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "transactions")
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Column(nullable = false)
    private LocalDate date;

    @NotNull
    @Positive
    @Column(nullable = false)
    private BigDecimal amount;

    private String description;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionType type;

    @NotNull
    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "account_id")
    private Account account;

    @NotNull
    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "category_id")
    private Category category;

    /**
     * When set, this movement is a refund (total or partial) of the referenced
     * expense. A refund shares the original's category and account; its presence
     * flips the sign in aggregations (it reduces spending and adds to balance).
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "refund_of_id")
    private Transaction refundOf;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public TransactionType getType() { return type; }
    public void setType(TransactionType type) { this.type = type; }
    public Account getAccount() { return account; }
    public void setAccount(Account account) { this.account = account; }
    public Category getCategory() { return category; }
    public void setCategory(Category category) { this.category = category; }
    public Transaction getRefundOf() { return refundOf; }
    public void setRefundOf(Transaction refundOf) { this.refundOf = refundOf; }
}
