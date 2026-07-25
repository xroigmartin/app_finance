package com.xroig.finance.investments.infrastructure.persistence;

import com.xroig.finance.investments.domain.Portfolio;
import com.xroig.finance.investments.domain.PortfolioId;
import com.xroig.finance.investments.domain.PortfolioRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/** Persistence adapter: implements the {@link PortfolioRepository} outbound port over JPA. */
@Component
public class PortfolioPersistenceAdapter implements PortfolioRepository {

    private final PortfolioJpaRepository jpa;
    private final PortfolioJpaMapper mapper;

    public PortfolioPersistenceAdapter(PortfolioJpaRepository jpa, PortfolioJpaMapper mapper) {
        this.jpa = jpa;
        this.mapper = mapper;
    }

    @Override
    public List<Portfolio> findAll() {
        return jpa.findAll().stream().map(mapper::toDomain).toList();
    }

    @Override
    public Optional<Portfolio> findById(PortfolioId id) {
        return jpa.findById(id.value()).map(mapper::toDomain);
    }

    @Override
    public Portfolio save(Portfolio portfolio) {
        return mapper.toDomain(jpa.save(mapper.toJpa(portfolio)));
    }

    @Override
    public void deleteById(PortfolioId id) {
        jpa.deleteById(id.value());
    }
}
