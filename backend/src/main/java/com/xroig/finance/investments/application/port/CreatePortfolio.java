package com.xroig.finance.investments.application.port;

import com.xroig.finance.investments.domain.Portfolio;

/** Inbound port: create a new portfolio (RF-1). */
public interface CreatePortfolio {

    Portfolio create(CreatePortfolioCommand command);

    /** Intent to create a portfolio, in the domain's own terms. */
    record CreatePortfolioCommand(String name, String baseCurrency) {
    }
}
