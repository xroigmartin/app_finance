package com.xroig.finance.transactions.infrastructure.persistence;

import com.xroig.finance.shared.domain.Money;
import com.xroig.finance.transactions.domain.Transaction;
import com.xroig.finance.transactions.domain.TransactionId;
import com.xroig.finance.transactions.domain.TransactionRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** Command-side persistence adapter: implements the {@link TransactionRepository} port over JPA. */
@Component
public class TransactionPersistenceAdapter implements TransactionRepository {

    private final TransactionJpaRepository jpa;
    private final TransactionJpaMapper mapper;

    public TransactionPersistenceAdapter(TransactionJpaRepository jpa, TransactionJpaMapper mapper) {
        this.jpa = jpa;
        this.mapper = mapper;
    }

    @Override
    public Optional<Transaction> findById(TransactionId id) {
        return jpa.findById(id.value()).map(mapper::toDomain);
    }

    @Override
    public Transaction save(Transaction transaction) {
        return mapper.toDomain(jpa.save(mapper.toJpa(transaction)));
    }

    @Override
    public void deleteById(TransactionId id) {
        jpa.deleteById(id.value());
    }

    @Override
    public Money refundedAmountFor(TransactionId originalId, TransactionId excludeId) {
        return Money.of(jpa.sumRefundedAmount(originalId.value(), excludeId == null ? null : excludeId.value()));
    }
}
