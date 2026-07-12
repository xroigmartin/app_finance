package com.xroig.finance.investments.infrastructure.persistence;

import com.xroig.finance.investments.domain.Portfolio;
import com.xroig.finance.investments.domain.PortfolioId;
import org.springframework.stereotype.Component;

/** Translates between the pure {@link Portfolio} aggregate and its {@link PortfolioJpaEntity}. */
@Component
public class PortfolioJpaMapper {

    public Portfolio toDomain(PortfolioJpaEntity entity) {
        return Portfolio.rehydrate(
                new PortfolioId(entity.getId()),
                entity.getName(),
                entity.getBaseCurrency());
    }

    public PortfolioJpaEntity toJpa(Portfolio portfolio) {
        PortfolioJpaEntity entity = new PortfolioJpaEntity();
        if (portfolio.id() != null) {
            entity.setId(portfolio.id().value());
        }
        entity.setName(portfolio.name());
        entity.setBaseCurrency(portfolio.baseCurrency());
        return entity;
    }
}
