package com.xroig.finance.investments.infrastructure.persistence;

import com.xroig.finance.investments.domain.InvestmentTransaction;
import com.xroig.finance.investments.domain.InvestmentTransactionId;
import com.xroig.finance.investments.domain.InvestmentTransactionRepository;
import com.xroig.finance.investments.domain.InvestmentTransactionType;
import com.xroig.finance.investments.domain.PortfolioId;
import com.xroig.finance.investments.domain.SecurityId;
import com.xroig.finance.shared.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Persistence adapter: implements the {@link InvestmentTransactionRepository}
 * outbound port over JPA, including the existence checks behind the RN-5 deletion
 * guards and the RN-10 import idempotency.
 */
@Component
public class InvestmentTransactionPersistenceAdapter implements InvestmentTransactionRepository {

    private final InvestmentTransactionJpaRepository jpa;
    private final InvestmentTransactionJpaMapper mapper;

    public InvestmentTransactionPersistenceAdapter(InvestmentTransactionJpaRepository jpa,
                                                   InvestmentTransactionJpaMapper mapper) {
        this.jpa = jpa;
        this.mapper = mapper;
    }

    @Override
    public Optional<InvestmentTransaction> findById(InvestmentTransactionId id) {
        return jpa.findById(id.value()).map(mapper::toDomain);
    }

    @Override
    public List<InvestmentTransaction> findByPortfolio(PortfolioId portfolioId) {
        return jpa.findByPortfolioIdOrderByTradeDateAscIdAsc(portfolioId.value()).stream()
                .map(mapper::toDomain)
                .toList();
    }

    private static final LocalDate SENTINEL_FROM = LocalDate.of(1970, 1, 1);
    private static final LocalDate SENTINEL_TO = LocalDate.of(2999, 12, 31);

    @Override
    public Page<InvestmentTransaction> search(PortfolioId portfolioId, InvestmentTransactionType type,
                                              LocalDate from, LocalDate to, SecurityId securityId,
                                              int page, int size) {
        org.springframework.data.domain.Page<InvestmentTransactionJpaEntity> result = jpa.search(
                portfolioId.value(), type == null ? null : type.name(),
                from != null ? from : SENTINEL_FROM, to != null ? to : SENTINEL_TO,
                securityId == null ? null : securityId.value(), PageRequest.of(page, size));
        return new Page<>(result.getContent().stream().map(mapper::toDomain).toList(),
                page, size, result.getTotalElements());
    }

    @Override
    public InvestmentTransaction save(InvestmentTransaction transaction) {
        return mapper.toDomain(jpa.save(mapper.toJpa(transaction)));
    }

    @Override
    public void deleteById(InvestmentTransactionId id) {
        jpa.deleteById(id.value());
    }

    @Override
    public boolean existsByPortfolio(PortfolioId portfolioId) {
        return jpa.existsByPortfolioId(portfolioId.value());
    }

    @Override
    public boolean existsBySecurity(SecurityId securityId) {
        return jpa.existsBySecurityId(securityId.value());
    }

    @Override
    public boolean existsByPortfolioAndExternalId(PortfolioId portfolioId, String externalId) {
        return jpa.existsByPortfolioIdAndExternalId(portfolioId.value(), externalId);
    }
}
