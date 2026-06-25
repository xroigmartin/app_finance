/**
 * Accounts bounded context — application layer. The inbound ports (use cases) live
 * in {@code application.port}; {@link com.xroig.finance.accounts.application.AccountService}
 * implements them by orchestrating the domain aggregate and the outbound ports.
 * May use Spring stereotypes, but never depends on {@code infrastructure}.
 */
package com.xroig.finance.accounts.application;
