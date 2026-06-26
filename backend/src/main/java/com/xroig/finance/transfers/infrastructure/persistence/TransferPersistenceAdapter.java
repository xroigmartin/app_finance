package com.xroig.finance.transfers.infrastructure.persistence;

import com.xroig.finance.transfers.domain.Transfer;
import com.xroig.finance.transfers.domain.TransferId;
import com.xroig.finance.transfers.domain.TransferRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** Command-side persistence adapter: implements the {@link TransferRepository} port over JPA. */
@Component
public class TransferPersistenceAdapter implements TransferRepository {

    private final TransferJpaRepository jpa;
    private final TransferJpaMapper mapper;

    public TransferPersistenceAdapter(TransferJpaRepository jpa, TransferJpaMapper mapper) {
        this.jpa = jpa;
        this.mapper = mapper;
    }

    @Override
    public Optional<Transfer> findById(TransferId id) {
        return jpa.findById(id.value()).map(mapper::toDomain);
    }

    @Override
    public Transfer save(Transfer transfer) {
        return mapper.toDomain(jpa.save(mapper.toJpa(transfer)));
    }

    @Override
    public void deleteById(TransferId id) {
        jpa.deleteById(id.value());
    }
}
