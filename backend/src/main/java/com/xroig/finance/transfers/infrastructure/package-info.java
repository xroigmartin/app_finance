/**
 * Transfers bounded context — infrastructure layer. {@code persistence} holds the JPA
 * entity, Spring Data repository, mapper and the adapters implementing the domain
 * outbound ports and the read-side query port; {@code web} holds the REST controller
 * and its request DTO. The {@code /import} endpoint forwards to the legacy import
 * service until H7.
 */
package com.xroig.finance.transfers.infrastructure;
