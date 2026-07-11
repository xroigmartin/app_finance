package com.xroig.finance.investments.application.port;

import com.xroig.finance.investments.domain.Portfolio;

/**
 * Inbound port: edit a portfolio (RF-1). Editing means renaming — the base
 * currency is immutable after creation (it anchors the RN-7a snapshots and the
 * import validation of §8).
 */
public interface UpdatePortfolio {

    Portfolio update(long id, UpdatePortfolioCommand command);

    /** Intent to edit a portfolio, in the domain's own terms. */
    record UpdatePortfolioCommand(String name) {
    }
}
