package com.xroig.finance.investments.application.port;

import com.xroig.finance.investments.domain.Portfolio;

import java.util.List;

/** Inbound port: list the portfolios (RF-1). */
public interface FindPortfolios {

    List<Portfolio> all();
}
