package com.xroig.finance.investments.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

/** Spring Data repository over {@code investments.price_quote}. */
public interface PriceQuoteJpaRepository extends JpaRepository<PriceQuoteJpaEntity, Long> {

    Optional<PriceQuoteJpaEntity> findBySecurityIdAndQuoteDate(Long securityId, LocalDate quoteDate);

    Optional<PriceQuoteJpaEntity> findFirstBySecurityIdAndQuoteDateLessThanEqualOrderByQuoteDateDesc(
            Long securityId, LocalDate date);
}
