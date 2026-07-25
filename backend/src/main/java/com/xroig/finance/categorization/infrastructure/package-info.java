/**
 * Categorization bounded context — infrastructure layer. Adapters that implement the
 * domain's outbound ports over JPA ({@code persistence}: the {@link
 * com.xroig.finance.categorization.infrastructure.persistence.CategoryRuleJpaEntity},
 * its mapper/repository, the command and read adapters, the {@code RuleCategoryCatalog}
 * and the {@code TransactionRecategorizer} ACL over the transactions store) and the
 * inbound REST adapter ({@code web}: {@link
 * com.xroig.finance.categorization.infrastructure.web.CategoryRuleController} +
 * {@code CategoryRuleRequest}).
 */
package com.xroig.finance.categorization.infrastructure;
