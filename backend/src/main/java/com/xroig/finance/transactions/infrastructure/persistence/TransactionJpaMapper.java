package com.xroig.finance.transactions.infrastructure.persistence;

import com.xroig.finance.accounts.domain.AccountId;
import com.xroig.finance.accounts.infrastructure.persistence.AccountJpaRepository;
import com.xroig.finance.categories.domain.CategoryId;
import com.xroig.finance.categories.infrastructure.persistence.CategoryJpaRepository;
import com.xroig.finance.shared.domain.Money;
import com.xroig.finance.transactions.domain.Transaction;
import com.xroig.finance.transactions.domain.TransactionId;
import org.springframework.stereotype.Component;

/**
 * Translates between the pure {@link Transaction} aggregate and its {@link
 * TransactionJpaEntity}. On write it resolves the account/category/refund associations
 * from their ids via {@code getReferenceById}; on read it takes only their ids back.
 */
@Component
public class TransactionJpaMapper {

    private final AccountJpaRepository accounts;
    private final CategoryJpaRepository categories;
    private final TransactionJpaRepository transactions;

    public TransactionJpaMapper(AccountJpaRepository accounts, CategoryJpaRepository categories,
                                TransactionJpaRepository transactions) {
        this.accounts = accounts;
        this.categories = categories;
        this.transactions = transactions;
    }

    public Transaction toDomain(TransactionJpaEntity entity) {
        TransactionId refundOfId = entity.getRefundOf() == null
                ? null : new TransactionId(entity.getRefundOf().getId());
        return Transaction.rehydrate(new TransactionId(entity.getId()), entity.getDate(),
                Money.of(entity.getAmount()), entity.getDescription(), entity.getType(),
                new AccountId(entity.getAccount().getId()), new CategoryId(entity.getCategory().getId()),
                refundOfId);
    }

    public TransactionJpaEntity toJpa(Transaction transaction) {
        TransactionJpaEntity entity = new TransactionJpaEntity();
        if (transaction.id() != null) {
            entity.setId(transaction.id().value());
        }
        entity.setDate(transaction.date());
        entity.setAmount(transaction.amount().amount());
        entity.setDescription(transaction.description());
        entity.setType(transaction.type());
        entity.setAccount(accounts.getReferenceById(transaction.accountId().value()));
        entity.setCategory(categories.getReferenceById(transaction.categoryId().value()));
        entity.setRefundOf(transaction.refundOfId() == null
                ? null : transactions.getReferenceById(transaction.refundOfId().value()));
        return entity;
    }
}
