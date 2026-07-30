package com.xroig.finance.investments.application;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * CQRS read model of one persisted Flex import attempt (§6): serialized as-is by
 * the web adapter, same pattern as the rest of the context's views.
 */
public record ImportRecordView(long id, Instant importedAt, String fileName, LocalDate fromDate, LocalDate toDate,
                               int imported, int duplicated, List<FlexRowError> errors, List<String> warnings) {
}
