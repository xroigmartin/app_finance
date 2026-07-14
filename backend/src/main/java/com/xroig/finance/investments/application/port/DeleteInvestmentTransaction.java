package com.xroig.finance.investments.application.port;

/**
 * Inbound port: delete an operation (RF-2). Deleting an imported row frees its
 * {@code external_id}, so reimporting the same period resurrects it — expected
 * v1 behavior (§9).
 */
public interface DeleteInvestmentTransaction {

    void delete(long id);
}
