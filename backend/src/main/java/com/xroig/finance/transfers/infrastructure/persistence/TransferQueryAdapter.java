package com.xroig.finance.transfers.infrastructure.persistence;

import com.xroig.finance.accounts.infrastructure.persistence.AccountJpaEntity;
import com.xroig.finance.transfers.application.TransferQueryPort;
import com.xroig.finance.transfers.application.TransferView;
import com.xroig.finance.transfers.application.TransferView.AccountRef;
import com.xroig.finance.transfers.domain.TransferId;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Read-side adapter (CQRS): assembles {@link TransferView} read models from the JPA entity graph. */
@Component
public class TransferQueryAdapter implements TransferQueryPort {

    private final TransferJpaRepository jpa;

    public TransferQueryAdapter(TransferJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public List<TransferView> search(LocalDate from, LocalDate to, Long accountId) {
        return jpa.search(from, to, accountId).stream().map(TransferQueryAdapter::toView).toList();
    }

    @Override
    public Optional<TransferView> findById(TransferId id) {
        return jpa.findById(id.value()).map(TransferQueryAdapter::toView);
    }

    private static TransferView toView(TransferJpaEntity entity) {
        return new TransferView(entity.getId(), entity.getDate(), entity.getAmount(), entity.getDescription(),
                toAccountRef(entity.getFromAccount()), toAccountRef(entity.getToAccount()));
    }

    private static AccountRef toAccountRef(AccountJpaEntity account) {
        return new AccountRef(account.getId(), account.getName(), account.getType(), account.getInitialBalance());
    }
}
