package com.xroig.finance.reporting.infrastructure.persistence;

import com.xroig.finance.reporting.application.MovementQueryPort;
import com.xroig.finance.reporting.application.MovementView;
import com.xroig.finance.reporting.infrastructure.persistence.MovementKeyRepository.MovementKeyProjection;
import com.xroig.finance.shared.domain.Page;
import com.xroig.finance.transactions.infrastructure.persistence.TransactionJpaEntity;
import com.xroig.finance.transactions.infrastructure.persistence.TransactionJpaRepository;
import com.xroig.finance.transactions.infrastructure.persistence.TransactionQueryAdapter;
import com.xroig.finance.transfers.infrastructure.persistence.TransferJpaEntity;
import com.xroig.finance.transfers.infrastructure.persistence.TransferJpaRepository;
import com.xroig.finance.transfers.infrastructure.persistence.TransferQueryAdapter;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Combines transactions + transfers into the paginated "Movimientos" feed
 * ({@link MovementQueryPort}): resolves the page's {@code (id, source)} keys with
 * {@link MovementKeyRepository}'s {@code union all} query (ordering + LIMIT/OFFSET
 * at the database), then hydrates each side's full row from its own repository —
 * reusing {@link TransactionQueryAdapter#toView}/{@link TransferQueryAdapter#toView}
 * so a movement is shaped exactly as `/api/transactions`/`/api/transfers` already do.
 */
@Component
class MovementQueryAdapter implements MovementQueryPort {

    private final MovementKeyRepository keys;
    private final TransactionJpaRepository transactions;
    private final TransferJpaRepository transfers;

    MovementQueryAdapter(MovementKeyRepository keys, TransactionJpaRepository transactions,
                        TransferJpaRepository transfers) {
        this.keys = keys;
        this.transactions = transactions;
        this.transfers = transfers;
    }

    @Override
    public Page<MovementView> search(LocalDate from, LocalDate to, Long accountId, Long categoryId,
                                     int page, int size) {
        List<MovementKeyProjection> pageKeys = keys.searchKeys(from, to, accountId, categoryId,
                PageRequest.of(page, size));

        List<Long> txIds = idsOf(pageKeys, "tx");
        List<Long> trIds = idsOf(pageKeys, "tr");
        Map<Long, TransactionJpaEntity> txById = toMap(transactions.findAllById(txIds), TransactionJpaEntity::getId);
        Map<Long, TransferJpaEntity> trById = toMap(transfers.findAllById(trIds), TransferJpaEntity::getId);

        List<MovementView> content = pageKeys.stream()
                .map(key -> toMovementView(key, txById, trById))
                .toList();

        long total = keys.countTransactions(from, to, accountId, categoryId)
                + keys.countTransfers(from, to, accountId, categoryId);
        return new Page<>(content, page, size, total);
    }

    private static MovementView toMovementView(MovementKeyProjection key,
                                               Map<Long, TransactionJpaEntity> txById,
                                               Map<Long, TransferJpaEntity> trById) {
        long id = key.getId();
        boolean isTx = "tx".equals(key.getSource());
        return new MovementView(key.getSource(), key.getSortDate(), id,
                isTx ? TransactionQueryAdapter.toView(txById.get(id)) : null,
                isTx ? null : TransferQueryAdapter.toView(trById.get(id)));
    }

    private static List<Long> idsOf(List<MovementKeyProjection> keys, String source) {
        return keys.stream().filter(k -> source.equals(k.getSource())).map(MovementKeyProjection::getId).toList();
    }

    private static <T> Map<Long, T> toMap(List<T> list, Function<T, Long> idOf) {
        Map<Long, T> map = new LinkedHashMap<>();
        list.forEach(item -> map.put(idOf.apply(item), item));
        return map;
    }
}
