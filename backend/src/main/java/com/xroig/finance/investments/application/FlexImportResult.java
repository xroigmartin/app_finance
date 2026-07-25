package com.xroig.finance.investments.application;

import java.util.List;

/**
 * Outcome of a Flex import (RF-4/§8): rows imported, rows skipped as already
 * present (idempotency by {@code external_id}, RN-10 — duplicates are expected,
 * not errors), per-row errors (unreadable/unsupported/invalid rows, the rest is
 * imported) and non-blocking warnings (sale without enough position, missing
 * rates — RN-4). Serialized as-is by the web adapter.
 */
public record FlexImportResult(int imported, int duplicated,
                               List<FlexRowError> errors, List<String> warnings) {
}
