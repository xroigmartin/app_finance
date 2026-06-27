package com.xroig.finance.budgets.infrastructure.persistence;

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
 * Persistence entity for one effective-dated amount of a {@link RecurringBudgetJpaEntity},
 * mapped to {@code recurring_budget_amounts}. {@code valido_desde} is normalized to the first
 * day of the month; the {@code uq_amount_vigencia} unique constraint on
 * {@code (recurring_budget_id, valido_desde)} is what the in-place reconciliation must respect.
 */
@Entity
@Table(name = "recurring_budget_amounts")
public class RecurrenceAmountJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "recurring_budget_id")
    private RecurringBudgetJpaEntity recurringBudget;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(name = "valido_desde", nullable = false)
    private LocalDate validoDesde;

    protected RecurrenceAmountJpaEntity() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public RecurringBudgetJpaEntity getRecurringBudget() {
        return recurringBudget;
    }

    public void setRecurringBudget(RecurringBudgetJpaEntity recurringBudget) {
        this.recurringBudget = recurringBudget;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public LocalDate getValidoDesde() {
        return validoDesde;
    }

    public void setValidoDesde(LocalDate validoDesde) {
        this.validoDesde = validoDesde;
    }
}
