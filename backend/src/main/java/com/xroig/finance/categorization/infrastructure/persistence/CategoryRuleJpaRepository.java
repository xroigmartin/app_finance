package com.xroig.finance.categorization.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository over {@link CategoryRuleJpaEntity}; an implementation detail of
 * the categorization adapters and the categories delete-guard.
 */
public interface CategoryRuleJpaRepository extends JpaRepository<CategoryRuleJpaEntity, Long> {

    boolean existsByCategoryId(Long categoryId);
}
