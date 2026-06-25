/**
 * Categories bounded context — infrastructure layer. {@code persistence} holds the
 * JPA entity, Spring Data repository, mapper and the adapters implementing the domain
 * outbound ports and the read-side query port; {@code web} holds the REST controller
 * (inbound adapter) and its request DTO. Depends inward on application/domain.
 */
package com.xroig.finance.categories.infrastructure;
