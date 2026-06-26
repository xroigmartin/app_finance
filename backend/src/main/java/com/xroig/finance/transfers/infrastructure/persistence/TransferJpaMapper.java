package com.xroig.finance.transfers.infrastructure.persistence;

import com.xroig.finance.accounts.domain.AccountId;
import com.xroig.finance.accounts.infrastructure.persistence.AccountJpaRepository;
import com.xroig.finance.shared.domain.Money;
import com.xroig.finance.transfers.domain.Transfer;
import com.xroig.finance.transfers.domain.TransferId;
import org.springframework.stereotype.Component;

/**
 * Translates between the pure {@link Transfer} aggregate and its {@link TransferJpaEntity}.
 * On write it resolves the two account ends from their ids via {@code getReferenceById};
 * on read it takes only their ids back.
 */
@Component
public class TransferJpaMapper {

    private final AccountJpaRepository accounts;

    public TransferJpaMapper(AccountJpaRepository accounts) {
        this.accounts = accounts;
    }

    public Transfer toDomain(TransferJpaEntity entity) {
        return Transfer.rehydrate(new TransferId(entity.getId()), entity.getDate(),
                Money.of(entity.getAmount()), entity.getDescription(),
                new AccountId(entity.getFromAccount().getId()), new AccountId(entity.getToAccount().getId()));
    }

    public TransferJpaEntity toJpa(Transfer transfer) {
        TransferJpaEntity entity = new TransferJpaEntity();
        if (transfer.id() != null) {
            entity.setId(transfer.id().value());
        }
        entity.setDate(transfer.date());
        entity.setAmount(transfer.amount().amount());
        entity.setDescription(transfer.description());
        entity.setFromAccount(accounts.getReferenceById(transfer.fromAccountId().value()));
        entity.setToAccount(accounts.getReferenceById(transfer.toAccountId().value()));
        return entity;
    }
}
