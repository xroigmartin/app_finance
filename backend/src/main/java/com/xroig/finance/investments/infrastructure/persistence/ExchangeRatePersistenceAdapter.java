package com.xroig.finance.investments.infrastructure.persistence;

import com.xroig.finance.investments.domain.ExchangeRate;
import com.xroig.finance.investments.domain.ExchangeRateRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Persistence adapter: implements the {@link ExchangeRateRepository} outbound port
 * over JPA. The upsert (RN-9) resolves the natural key (date, pair) first and
 * overwrites the existing row's rate, so a reimport never duplicates a rate; the
 * {@code UNIQUE (rate_date, from_currency, to_currency)} constraint is the
 * physical backstop.
 */
@Component
public class ExchangeRatePersistenceAdapter implements ExchangeRateRepository {

    private final ExchangeRateJpaRepository jpa;

    public ExchangeRatePersistenceAdapter(ExchangeRateJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public void upsert(ExchangeRate rate) {
        ExchangeRateJpaEntity entity = jpa
                .findByRateDateAndFromCurrencyAndToCurrency(
                        rate.rateDate(), rate.fromCurrency(), rate.toCurrency())
                .orElseGet(() -> {
                    ExchangeRateJpaEntity created = new ExchangeRateJpaEntity();
                    created.setRateDate(rate.rateDate());
                    created.setFromCurrency(rate.fromCurrency());
                    created.setToCurrency(rate.toCurrency());
                    return created;
                });
        entity.setRate(rate.rate());
        jpa.save(entity);
    }

    @Override
    public Optional<ExchangeRate> find(LocalDate rateDate, String fromCurrency, String toCurrency) {
        return jpa.findByRateDateAndFromCurrencyAndToCurrency(rateDate, fromCurrency, toCurrency)
                .map(this::toDomain);
    }

    @Override
    public List<ExchangeRate> findAll() {
        return jpa.findAll().stream().map(this::toDomain).toList();
    }

    private ExchangeRate toDomain(ExchangeRateJpaEntity entity) {
        return new ExchangeRate(entity.getRateDate(), entity.getFromCurrency(),
                entity.getToCurrency(), entity.getRate());
    }
}
