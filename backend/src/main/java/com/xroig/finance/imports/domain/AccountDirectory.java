package com.xroig.finance.imports.domain;

import java.util.List;

/**
 * Outbound port: the accounts the import can resolve names against. The use case
 * matches by name (case-insensitive) and picks the default by id, so the directory
 * only needs to list them. Bridged to the accounts context.
 */
public interface AccountDirectory {

    List<ImportAccount> all();

    /** An account as the import sees it: just the identity and the name to match. */
    record ImportAccount(long id, String name) {
    }
}
