/**
 * Transfers bounded context — domain layer. Pure of Spring and JPA: the {@link
 * com.xroig.finance.transfers.domain.Transfer} aggregate (with the distinct-ends
 * invariant), {@link com.xroig.finance.transfers.domain.TransferId}, and the outbound
 * ports {@link com.xroig.finance.transfers.domain.TransferRepository} and {@link
 * com.xroig.finance.transfers.domain.AccountExistence}.
 */
package com.xroig.finance.transfers.domain;
