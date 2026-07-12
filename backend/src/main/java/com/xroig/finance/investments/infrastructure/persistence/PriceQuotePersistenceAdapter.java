package com.xroig.finance.investments.infrastructure.persistence;

import com.xroig.finance.investments.domain.PriceQuote;
import com.xroig.finance.investments.domain.PriceQuoteRepository;
import com.xroig.finance.investments.domain.SecurityId;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Persistence adapter: implements the {@link PriceQuoteRepository} outbound port
 * over JPA. The upsert (RN-9) resolves the natural key (security, date) first and
 * overwrites the existing row's price, so a reimport never duplicates a quote;
 * the {@code UNIQUE (security_id, quote_date)} constraint is the physical backstop.
 */
@Component
public class PriceQuotePersistenceAdapter implements PriceQuoteRepository {

    private final PriceQuoteJpaRepository jpa;

    public PriceQuotePersistenceAdapter(PriceQuoteJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public void upsert(PriceQuote quote) {
        PriceQuoteJpaEntity entity = jpa
                .findBySecurityIdAndQuoteDate(quote.securityId().value(), quote.quoteDate())
                .orElseGet(() -> {
                    PriceQuoteJpaEntity created = new PriceQuoteJpaEntity();
                    created.setSecurityId(quote.securityId().value());
                    created.setQuoteDate(quote.quoteDate());
                    return created;
                });
        entity.setPrice(quote.price());
        jpa.save(entity);
    }

    @Override
    public Optional<PriceQuote> find(SecurityId securityId, LocalDate quoteDate) {
        return jpa.findBySecurityIdAndQuoteDate(securityId.value(), quoteDate).map(this::toDomain);
    }

    @Override
    public Optional<PriceQuote> findLatestOnOrBefore(SecurityId securityId, LocalDate date) {
        return jpa.findFirstBySecurityIdAndQuoteDateLessThanEqualOrderByQuoteDateDesc(
                securityId.value(), date).map(this::toDomain);
    }

    private PriceQuote toDomain(PriceQuoteJpaEntity entity) {
        return PriceQuote.of(new SecurityId(entity.getSecurityId()), entity.getQuoteDate(), entity.getPrice());
    }
}
