/**
 * Transactions bounded context — application layer. Inbound use-case ports live in
 * {@code application.port}; the read side is {@link
 * com.xroig.finance.transactions.application.TransactionQueryPort} + {@link
 * com.xroig.finance.transactions.application.TransactionView} (CQRS read model).
 * {@link com.xroig.finance.transactions.application.TransactionService} implements the
 * use cases by orchestrating the domain aggregate and the outbound ports.
 */
package com.xroig.finance.transactions.application;
