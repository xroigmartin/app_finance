package com.xroig.finance.investments.domain;

import java.util.List;
import java.util.Optional;

/**
 * Outbound port: how the application persists and reads {@link Portfolio}
 * aggregates. The domain owns the contract; a persistence adapter in
 * {@code infrastructure} implements it against the {@code investments} schema.
 */
public interface PortfolioRepository {

    List<Portfolio> findAll();

    Optional<Portfolio> findById(PortfolioId id);

    Portfolio save(Portfolio portfolio);

    void deleteById(PortfolioId id);
}
