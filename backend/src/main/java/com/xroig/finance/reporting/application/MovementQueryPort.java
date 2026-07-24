package com.xroig.finance.reporting.application;

import com.xroig.finance.shared.domain.Page;

import java.time.LocalDate;

/**
 * Read-side (CQRS) outbound port: the combined, paginated "Movimientos" feed
 * (transactions + transfers), newest first. A plain client-side merge of two
 * independently-paginated lists can't paginate correctly across sources, so
 * filtering, ordering and paging all happen at the database (see {@code
 * MovementQueryAdapter}). A null {@code accountId}/{@code categoryId} does not
 * filter; a category filter hides transfers entirely (they have no category).
 */
public interface MovementQueryPort {

    Page<MovementView> search(LocalDate from, LocalDate to, Long accountId, Long categoryId, int page, int size);
}
