package com.xroig.finance.investments.application;

import com.xroig.finance.shared.domain.Page;

/**
 * Inbound port (CQRS): the paginated import history of a portfolio (§6), newest
 * first. Unlike {@code ImportRecordRepository} (write-only from the domain's
 * side), this is the read side.
 */
public interface ImportRecordQueryPort {

    Page<ImportRecordView> history(long portfolioId, int page, int size);
}
