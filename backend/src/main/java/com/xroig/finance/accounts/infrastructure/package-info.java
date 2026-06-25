/**
 * Accounts bounded context — infrastructure layer. {@code persistence} holds the
 * JPA entity, Spring Data repository, mapper and the adapters implementing the
 * domain's outbound ports; {@code web} holds the REST controller (inbound adapter)
 * and its DTOs. Depends inward on application/domain, never the reverse.
 */
package com.xroig.finance.accounts.infrastructure;
