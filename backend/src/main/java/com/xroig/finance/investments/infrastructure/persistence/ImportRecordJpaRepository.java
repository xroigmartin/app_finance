package com.xroig.finance.investments.infrastructure.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data repository over {@code investments.import_record}. */
public interface ImportRecordJpaRepository extends JpaRepository<ImportRecordJpaEntity, Long> {

    Page<ImportRecordJpaEntity> findByPortfolioIdOrderByImportedAtDesc(Long portfolioId, Pageable pageable);
}
