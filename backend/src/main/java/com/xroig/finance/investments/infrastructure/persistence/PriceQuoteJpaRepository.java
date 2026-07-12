package com.xroig.finance.investments.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Spring Data repository over {@code investments.price_quote}. */
public interface PriceQuoteJpaRepository extends JpaRepository<PriceQuoteJpaEntity, Long> {

    Optional<PriceQuoteJpaEntity> findBySecurityIdAndQuoteDate(Long securityId, LocalDate quoteDate);

    List<PriceQuoteJpaEntity> findBySecurityIdIn(Collection<Long> securityIds);

    Optional<PriceQuoteJpaEntity> findFirstBySecurityIdAndQuoteDateLessThanEqualOrderByQuoteDateDesc(
            Long securityId, LocalDate date);
}
