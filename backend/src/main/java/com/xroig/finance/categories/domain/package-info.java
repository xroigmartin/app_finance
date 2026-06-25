/**
 * Categories bounded context — domain layer. Pure of Spring and JPA: the {@link
 * com.xroig.finance.categories.domain.Category} aggregate with its hierarchy and
 * type invariants, the {@link com.xroig.finance.categories.domain.CategoryScope}
 * value object, {@link com.xroig.finance.categories.domain.CategoryId}, and the
 * outbound ports the infrastructure implements ({@link
 * com.xroig.finance.categories.domain.CategoryRepository}, {@link
 * com.xroig.finance.categories.domain.CategoryReferences}, {@link
 * com.xroig.finance.categories.domain.AccountExistence}).
 */
package com.xroig.finance.categories.domain;
