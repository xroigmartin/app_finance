package com.xroig.finance.investments.domain;

import java.util.List;
import java.util.Optional;

/**
 * Outbound port: how the application persists and reads {@link Security}
 * aggregates. Exposes the lookup by business identity (ISIN + quote currency) used
 * by the duplicate guard and by the import's automatic registration (H1.10).
 */
public interface SecurityRepository {

    List<Security> findAll();

    Optional<Security> findById(SecurityId id);

    Optional<Security> findByIsinAndCurrency(String isin, String currency);

    Security save(Security security);

    void deleteById(SecurityId id);
}
