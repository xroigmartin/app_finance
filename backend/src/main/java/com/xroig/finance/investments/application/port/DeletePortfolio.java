package com.xroig.finance.investments.application.port;

/** Inbound port: delete a portfolio without operations (RF-1, guard RN-5). */
public interface DeletePortfolio {

    void delete(long id);
}
