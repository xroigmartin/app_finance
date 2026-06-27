package com.xroig.finance.categorization.application;

/**
 * Result of creating or updating a rule: the saved rule plus how many fallback movements
 * ("Otros gastos"/"Otros ingresos") were moved to the rule's category as a side effect.
 */
public record RuleSaved(CategoryRuleView rule, int recategorized) {
}
