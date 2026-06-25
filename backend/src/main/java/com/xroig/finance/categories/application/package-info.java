/**
 * Categories bounded context — application layer. Inbound use-case ports live in
 * {@code application.port}; the read side is {@link
 * com.xroig.finance.categories.application.CategoryQueryPort} +
 * {@link com.xroig.finance.categories.application.CategoryView} (CQRS read model).
 * {@link com.xroig.finance.categories.application.CategoryService} implements the
 * use cases, orchestrating the domain aggregate and the outbound ports.
 */
package com.xroig.finance.categories.application;
