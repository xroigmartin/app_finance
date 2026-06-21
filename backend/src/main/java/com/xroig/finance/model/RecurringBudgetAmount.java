package com.xroig.finance.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One amount of a {@link RecurringBudget}, valid from a given month onwards
 * ({@code validoDesde} normalized to the first day of the month). The history of
 * these rows lets a recurring payment change its amount over time while keeping
 * past months on the previous amount.
 */
@Entity
@Table(name = "recurring_budget_amounts")
public class RecurringBudgetAmount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "recurring_budget_id")
    private RecurringBudget recurringBudget;

    @NotNull
    @Positive
    @Column(nullable = false)
    private BigDecimal amount;

    /** First day of the month from which this amount is in force. */
    @NotNull
    @Column(name = "valido_desde", nullable = false)
    private LocalDate validoDesde;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public RecurringBudget getRecurringBudget() { return recurringBudget; }
    public void setRecurringBudget(RecurringBudget recurringBudget) { this.recurringBudget = recurringBudget; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public LocalDate getValidoDesde() { return validoDesde; }
    public void setValidoDesde(LocalDate validoDesde) { this.validoDesde = validoDesde; }
}
