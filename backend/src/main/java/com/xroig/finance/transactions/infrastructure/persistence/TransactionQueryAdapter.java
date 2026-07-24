package com.xroig.finance.transactions.infrastructure.persistence;

import com.xroig.finance.accounts.infrastructure.persistence.AccountJpaEntity;
import com.xroig.finance.categories.infrastructure.persistence.CategoryJpaEntity;
import com.xroig.finance.transactions.application.TransactionQueryPort;
import com.xroig.finance.transactions.application.TransactionView;
import com.xroig.finance.transactions.application.TransactionView.AccountRef;
import com.xroig.finance.transactions.application.TransactionView.CategoryRef;
import com.xroig.finance.transactions.application.TransactionView.RefundRef;
import com.xroig.finance.transactions.domain.TransactionId;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Read-side adapter (CQRS): assembles {@link TransactionView} read models from the JPA entity graph. */
@Component
public class TransactionQueryAdapter implements TransactionQueryPort {

    private final TransactionJpaRepository jpa;

    public TransactionQueryAdapter(TransactionJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public List<TransactionView> search(LocalDate from, LocalDate to, Long accountId, Long categoryId) {
        return jpa.search(from, to, accountId, categoryId).stream().map(TransactionQueryAdapter::toView).toList();
    }

    @Override
    public List<TransactionView> recent() {
        return jpa.findTop10ByOrderByDateDescIdDesc().stream().map(TransactionQueryAdapter::toView).toList();
    }

    @Override
    public Optional<TransactionView> findById(TransactionId id) {
        return jpa.findById(id.value()).map(TransactionQueryAdapter::toView);
    }

    /** Reused by {@code reporting}'s combined movements feed to hydrate the transaction side. */
    public static TransactionView toView(TransactionJpaEntity entity) {
        RefundRef refundOf = entity.getRefundOf() == null ? null : new RefundRef(entity.getRefundOf().getId());
        return new TransactionView(entity.getId(), entity.getDate(), entity.getAmount(), entity.getDescription(),
                entity.getType(), toAccountRef(entity.getAccount()), toCategoryRef(entity.getCategory()), refundOf);
    }

    private static AccountRef toAccountRef(AccountJpaEntity account) {
        return new AccountRef(account.getId(), account.getName(), account.getType(), account.getInitialBalance());
    }

    private static CategoryRef toCategoryRef(CategoryJpaEntity category) {
        return new CategoryRef(category.getId(), category.getName(), category.getType(), category.getColor());
    }
}
