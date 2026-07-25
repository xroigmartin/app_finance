package com.xroig.finance.categorization.application.port;

import com.xroig.finance.categorization.application.RuleSaved;
import com.xroig.finance.categorization.application.port.CreateRule.RuleCommand;

/** Inbound port: edit a rule and re-apply it to the fallback movements. */
public interface UpdateRule {

    RuleSaved update(long id, RuleCommand command);
}
