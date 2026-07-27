package com.xroig.finance.reporting.application.port;

import com.xroig.finance.reporting.application.MovementView;
import com.xroig.finance.shared.domain.Page;

import java.time.LocalDate;

/**
 * Inbound port: the combined, paginated "Movimientos" feed (transactions +
 * transfers, §see reporting PRD). Implemented by {@code ReportingService},
 * delegating to {@link com.xroig.finance.reporting.application.MovementQueryPort}.
 */
public interface FindMovements {

    Page<MovementView> findMovements(LocalDate from, LocalDate to, Long accountId, Long categoryId,
                                     int page, int size);
}
