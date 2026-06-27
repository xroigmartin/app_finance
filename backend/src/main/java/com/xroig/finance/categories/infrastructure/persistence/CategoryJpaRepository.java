package com.xroig.finance.categories.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/** Spring Data repository over {@link CategoryJpaEntity}; an implementation detail of the adapters. */
public interface CategoryJpaRepository extends JpaRepository<CategoryJpaEntity, Long> {

    boolean existsByParentId(Long parentId);

    List<CategoryJpaEntity> findByParentId(Long parentId);

    /** Categories usable on a given account: the global ones plus those owned by that account. */
    @Query("""
            select c from CategoryJpaEntity c
            where c.account is null or c.account.id = :accountId
            """)
    List<CategoryJpaEntity> findVisibleForAccount(@Param("accountId") Long accountId);
}
