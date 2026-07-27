package com.xroig.finance.investments.infrastructure.persistence;

import com.xroig.finance.investments.domain.Security;
import com.xroig.finance.investments.domain.SecurityId;
import org.springframework.stereotype.Component;

/** Translates between the pure {@link Security} aggregate and its {@link SecurityJpaEntity}. */
@Component
public class SecurityJpaMapper {

    public Security toDomain(SecurityJpaEntity entity) {
        return Security.rehydrate(
                new SecurityId(entity.getId()),
                entity.getIsin(),
                entity.getCurrency(),
                entity.getName(),
                entity.getTicker(),
                entity.getType(),
                entity.getExchange(),
                entity.getFigi());
    }

    public SecurityJpaEntity toJpa(Security security) {
        SecurityJpaEntity entity = new SecurityJpaEntity();
        if (security.id() != null) {
            entity.setId(security.id().value());
        }
        entity.setIsin(security.isin());
        entity.setCurrency(security.currency());
        entity.setName(security.name());
        entity.setTicker(security.ticker());
        entity.setType(security.type());
        entity.setExchange(security.exchange());
        entity.setFigi(security.figi());
        return entity;
    }
}
