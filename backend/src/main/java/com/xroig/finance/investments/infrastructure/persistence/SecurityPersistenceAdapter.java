package com.xroig.finance.investments.infrastructure.persistence;

import com.xroig.finance.investments.domain.Security;
import com.xroig.finance.investments.domain.SecurityId;
import com.xroig.finance.investments.domain.SecurityRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/** Persistence adapter: implements the {@link SecurityRepository} outbound port over JPA. */
@Component
public class SecurityPersistenceAdapter implements SecurityRepository {

    private final SecurityJpaRepository jpa;
    private final SecurityJpaMapper mapper;

    public SecurityPersistenceAdapter(SecurityJpaRepository jpa, SecurityJpaMapper mapper) {
        this.jpa = jpa;
        this.mapper = mapper;
    }

    @Override
    public List<Security> findAll() {
        return jpa.findAll().stream().map(mapper::toDomain).toList();
    }

    @Override
    public Optional<Security> findById(SecurityId id) {
        return jpa.findById(id.value()).map(mapper::toDomain);
    }

    @Override
    public Optional<Security> findByIsinAndCurrency(String isin, String currency) {
        return jpa.findByIsinAndCurrency(isin, currency).map(mapper::toDomain);
    }

    @Override
    public Security save(Security security) {
        return mapper.toDomain(jpa.save(mapper.toJpa(security)));
    }

    @Override
    public void deleteById(SecurityId id) {
        jpa.deleteById(id.value());
    }
}
