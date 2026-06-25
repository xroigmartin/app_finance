package com.xroig.finance.categories.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** Spring Data repository over {@link CategoryJpaEntity}; an implementation detail of the adapters. */
public interface CategoryJpaRepository extends JpaRepository<CategoryJpaEntity, Long> {

    boolean existsByParentId(Long parentId);

    List<CategoryJpaEntity> findByParentId(Long parentId);
}
