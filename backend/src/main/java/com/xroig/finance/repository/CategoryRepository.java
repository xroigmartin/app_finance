package com.xroig.finance.repository;

import com.xroig.finance.model.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    /**
     * Categories usable on a given account: the global ones plus those owned
     * by that account.
     */
    @Query("""
            select c from Category c
            where c.account is null or c.account.id = :accountId
            """)
    List<Category> findVisibleForAccount(@Param("accountId") Long accountId);

    boolean existsByParentId(Long parentId);

    List<Category> findByParentId(Long parentId);
}
