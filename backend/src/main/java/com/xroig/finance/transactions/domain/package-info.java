/**
 * Transactions bounded context — domain layer. Pure of Spring and JPA: the {@link
 * com.xroig.finance.transactions.domain.Transaction} aggregate (with the refund
 * invariant), {@link com.xroig.finance.transactions.domain.TransactionId}, and the
 * outbound ports {@link com.xroig.finance.transactions.domain.TransactionRepository},
 * {@link com.xroig.finance.transactions.domain.AccountExistence} and {@link
 * com.xroig.finance.transactions.domain.CategoryCatalog}.
 */
package com.xroig.finance.transactions.domain;
