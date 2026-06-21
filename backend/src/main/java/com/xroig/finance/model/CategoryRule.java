package com.xroig.finance.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Auto-categorization rule used by the import: when a row has no explicit
 * category, the first rule whose pattern matches the description assigns
 * its category. The pattern is a case/accent-insensitive substring;
 * several alternatives can be separated with "|".
 */
@Entity
@Table(name = "category_rules")
public class CategoryRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(nullable = false)
    private String pattern;

    @NotNull
    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "category_id")
    private Category category;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getPattern() { return pattern; }
    public void setPattern(String pattern) { this.pattern = pattern; }
    public Category getCategory() { return category; }
    public void setCategory(Category category) { this.category = category; }
}
