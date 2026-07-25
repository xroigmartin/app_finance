package com.xroig.finance.categorization.infrastructure.persistence;

import com.xroig.finance.categories.infrastructure.persistence.CategoryJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Persistence entity for the categorization context, mapped to the existing {@code
 * category_rules} table (Flyway-owned; {@code ddl-auto=validate}). Kept separate from the
 * pure {@link com.xroig.finance.categorization.domain.CategoryRule} aggregate.
 *
 * <p>The target category is mapped as a {@code @ManyToOne} association on {@code
 * category_id}. The aggregate references it only by id, so the mapper sets it from {@code
 * getReferenceById} on write and reads {@code getId()} on read; the read adapter navigates
 * it (eagerly) to assemble the nested category in the read model.
 */
@Entity
@Table(name = "category_rules")
public class CategoryRuleJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String pattern;

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "category_id")
    private CategoryJpaEntity category;

    protected CategoryRuleJpaEntity() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getPattern() {
        return pattern;
    }

    public void setPattern(String pattern) {
        this.pattern = pattern;
    }

    public CategoryJpaEntity getCategory() {
        return category;
    }

    public void setCategory(CategoryJpaEntity category) {
        this.category = category;
    }
}
