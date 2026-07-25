package com.xroig.finance.investments.domain;

import java.time.LocalDate;

/**
 * Non-blocking incident found while computing positions: a sale without enough
 * position (missing earlier history, RN-4) or a missing exchange rate for a cost
 * component. The calculator flags — whether it becomes a hard 400 (manual entry)
 * or an import warning is the use case's call. {@code securityId} may be null when
 * the incident is not tied to an instrument.
 */
public record PositionWarning(SecurityId securityId, LocalDate tradeDate, String message) {
}
