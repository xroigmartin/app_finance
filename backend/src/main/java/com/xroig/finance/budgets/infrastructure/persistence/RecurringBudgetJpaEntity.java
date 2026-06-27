package com.xroig.finance.budgets.infrastructure.persistence;

import com.xroig.finance.categories.infrastructure.persistence.CategoryJpaEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.util.ArrayList;
import java.util.List;

/**
 * Persistence entity for the recurrence, mapped to the existing {@code recurring_budgets}
 * table (Flyway-owned; {@code ddl-auto=validate}). Kept separate from the pure
 * {@link com.xroig.finance.budgets.domain.RecurringBudget} aggregate; the owning category is a
 * {@code @OneToOne} resolved by id via {@code getReferenceById}. The amount history is a
 * {@code @OneToMany} with {@code orphanRemoval} so the in-place reconciliation in the mapper
 * physically deletes dropped rows.
 */
@Entity
@Table(name = "recurring_budgets")
public class RecurringBudgetJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "category_id", unique = true)
    private CategoryJpaEntity category;

    @Column(nullable = false)
    private Integer months;

    @Column(nullable = false)
    private boolean active = true;

    @OneToMany(mappedBy = "recurringBudget", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RecurrenceAmountJpaEntity> amounts = new ArrayList<>();

    protected RecurringBudgetJpaEntity() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public CategoryJpaEntity getCategory() {
        return category;
    }

    public void setCategory(CategoryJpaEntity category) {
        this.category = category;
    }

    public Integer getMonths() {
        return months;
    }

    public void setMonths(Integer months) {
        this.months = months;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public List<RecurrenceAmountJpaEntity> getAmounts() {
        return amounts;
    }

    public void setAmounts(List<RecurrenceAmountJpaEntity> amounts) {
        this.amounts = amounts;
    }
}
