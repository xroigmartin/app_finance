/**
 * Imports bounded context — application layer. Inbound use-case ports live in
 * {@code application.port} ({@link com.xroig.finance.imports.application.port.ImportTransactions}
 * / {@link com.xroig.finance.imports.application.port.ImportTransfers}); {@link
 * com.xroig.finance.imports.application.ImportFileReader} is the outbound port that
 * turns an upload into rows (implemented by the parser adapter). {@link
 * com.xroig.finance.imports.application.ImportService} orchestrates row resolution,
 * categorization and deduplication, reusing the create use cases of the other
 * contexts through the domain outbound ports, and returns {@link
 * com.xroig.finance.imports.application.ImportResult}.
 */
package com.xroig.finance.imports.application;
