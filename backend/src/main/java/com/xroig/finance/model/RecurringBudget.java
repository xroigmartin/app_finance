package com.xroig.finance.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Recurring planned payment attached to a leaf, account-bound category
 * (e.g. comunidad, hipoteca). It only feeds the "planned" side of the annual
 * budget matrix; it never creates real transactions.
 *
 * <p>The active months are stored as a 12-bit mask ({@code bit 0 = January …
 * bit 11 = December}). The amount is effective-dated: {@link #amounts} keeps the
 * history of amount changes, and the amount in force for a given month is the
 * most recent one whose {@code validoDesde} is on or before that month.
 */
@Entity
@Table(name = "recurring_budgets")
public class RecurringBudget {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Owning category; the account and type are derived from it. */
    @NotNull
    @OneToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "category_id", unique = true)
    private Category category;

    /** 12-bit mask of active months (bit 0 = January … bit 11 = December). */
    @NotNull
    @Column(nullable = false)
    private Integer months;

    @Column(nullable = false)
    private boolean active = true;

    @OneToMany(mappedBy = "recurringBudget", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RecurringBudgetAmount> amounts = new ArrayList<>();

    /** True when the given calendar month (1..12) is part of the recurrence. */
    public boolean appliesToMonth(int month) {
        return (months & (1 << (month - 1))) != 0;
    }

    /**
     * Amount in force on the given date: the most recent {@code validoDesde} that
     * is on or before {@code date}, or {@code null} if the recurrence has not
     * started yet by then.
     */
    public BigDecimal amountAt(LocalDate date) {
        BigDecimal best = null;
        LocalDate bestDate = null;
        for (RecurringBudgetAmount a : amounts) {
            if (!a.getValidoDesde().isAfter(date)
                    && (bestDate == null || a.getValidoDesde().isAfter(bestDate))) {
                bestDate = a.getValidoDesde();
                best = a.getAmount();
            }
        }
        return best;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Category getCategory() { return category; }
    public void setCategory(Category category) { this.category = category; }
    public Integer getMonths() { return months; }
    public void setMonths(Integer months) { this.months = months; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public List<RecurringBudgetAmount> getAmounts() { return amounts; }
    public void setAmounts(List<RecurringBudgetAmount> amounts) { this.amounts = amounts; }
}
