/**
 * Categorization bounded context — application layer. The {@link
 * com.xroig.finance.categorization.application.CategoryRuleService} implements the inbound
 * ports ({@code FindRules}/{@code CreateRule}/{@code UpdateRule}/{@code DeleteRule}),
 * orchestrating the aggregate and the outbound ports. Reads use the CQRS {@link
 * com.xroig.finance.categorization.application.CategoryRuleQueryPort} and the {@link
 * com.xroig.finance.categorization.application.CategoryRuleView} read model.
 */
package com.xroig.finance.categorization.application;
