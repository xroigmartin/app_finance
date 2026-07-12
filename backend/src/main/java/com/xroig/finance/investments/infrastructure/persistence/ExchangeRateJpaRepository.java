package com.xroig.finance.investments.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

/** Spring Data repository over {@code investments.exchange_rate}. */
public interface ExchangeRateJpaRepository extends JpaRepository<ExchangeRateJpaEntity, Long> {

    Optional<ExchangeRateJpaEntity> findByRateDateAndFromCurrencyAndToCurrency(
            LocalDate rateDate, String fromCurrency, String toCurrency);
}
