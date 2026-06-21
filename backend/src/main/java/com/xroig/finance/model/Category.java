package com.xroig.finance.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * A category is either global ({@code account == null}, common to every
 * account) or owned by a single account. Names are unique within their
 * scope: one global "Luz" and one "Luz" per account may coexist.
 */
@Entity
@Table(name = "categories")
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(nullable = false)
    private String name;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionType type;

    @Column(nullable = false)
    private String color = "#6366f1";

    /** Null for global categories; otherwise the owning account. */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "account_id")
    private Account account;

    /**
     * Null for top-level categories; otherwise the parent category. Only one
     * level of nesting is allowed (a subcategory cannot have its own children).
     * Subcategories inherit their parent's type. The scope is inherited only
     * when the parent is account-bound; a global parent may have global or
     * account-specific subcategories.
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "parent_id")
    private Category parent;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public TransactionType getType() { return type; }
    public void setType(TransactionType type) { this.type = type; }
    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }
    public Account getAccount() { return account; }
    public void setAccount(Account account) { this.account = account; }
    public Category getParent() { return parent; }
    public void setParent(Category parent) { this.parent = parent; }
}
