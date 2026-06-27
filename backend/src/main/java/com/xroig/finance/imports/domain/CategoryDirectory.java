package com.xroig.finance.imports.domain;

import com.xroig.finance.shared.domain.TransactionType;

import java.util.List;

/**
 * Outbound port: the categories the import can resolve names against, plus the
 * ability to create an unknown one on the fly (always global, as the legacy import
 * did). The name/scope matching stays in the use case; the directory only lists and
 * creates. Bridged to the categories context.
 */
public interface CategoryDirectory {

    List<ImportCategory> all();

    /** Creates a brand-new global category of the given type and returns it (with its id). */
    ImportCategory createGlobal(String name, TransactionType type);

    /** A category as the import sees it; {@code accountId} null means a global (cross-account) one. */
    record ImportCategory(long id, String name, TransactionType type, Long accountId) {

        public boolean global() {
            return accountId == null;
        }
    }
}
