package com.xroig.finance.categorization.application.port;

import com.xroig.finance.categorization.application.RuleSaved;

/** Inbound port: create a rule and re-apply it to the fallback movements. */
public interface CreateRule {

    RuleSaved create(RuleCommand command);

    /** Intent to create/update a rule. */
    record RuleCommand(String pattern, Long categoryId) {
    }
}
