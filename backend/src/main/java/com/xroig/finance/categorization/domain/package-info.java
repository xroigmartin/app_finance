/**
 * Categorization bounded context — domain layer. Pure of Spring and JPA: the {@link
 * com.xroig.finance.categorization.domain.CategoryRule} aggregate (with its pattern
 * invariant and {@link com.xroig.finance.categorization.domain.CategoryRule#matches
 * matching} behaviour), the {@link com.xroig.finance.categorization.domain.PatternMatcher}
 * domain service, {@link com.xroig.finance.categorization.domain.CategoryRuleId}, and the
 * outbound ports the infrastructure implements ({@link
 * com.xroig.finance.categorization.domain.CategoryRuleRepository}, {@link
 * com.xroig.finance.categorization.domain.RuleCategoryCatalog}, {@link
 * com.xroig.finance.categorization.domain.TransactionRecategorizer}).
 */
package com.xroig.finance.categorization.domain;
