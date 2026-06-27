package com.xroig.finance.categorization.application.port;

import com.xroig.finance.categorization.application.CategoryRuleView;

import java.util.List;

/** Inbound port: list all category rules. */
public interface FindRules {

    List<CategoryRuleView> findAll();
}
