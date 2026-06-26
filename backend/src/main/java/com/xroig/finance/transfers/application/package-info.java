/**
 * Transfers bounded context — application layer. Inbound use-case ports live in
 * {@code application.port}; the read side is {@link
 * com.xroig.finance.transfers.application.TransferQueryPort} + {@link
 * com.xroig.finance.transfers.application.TransferView} (CQRS read model). {@link
 * com.xroig.finance.transfers.application.TransferService} implements the use cases.
 */
package com.xroig.finance.transfers.application;
