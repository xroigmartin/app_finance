package com.xroig.finance.investments.application.port;

import com.xroig.finance.investments.application.InvestmentTransactionView;
import com.xroig.finance.investments.application.port.CreateInvestmentTransaction.InvestmentTransactionCommand;

/**
 * Inbound port: edit an operation (RF-2). Rebuilds the aggregate from the command
 * re-checking every invariant (§8) and the RN-4 hard guard on sales, preserving
 * the identity, the portfolio and the {@code external_id} of the stored row.
 */
public interface UpdateInvestmentTransaction {

    InvestmentTransactionView update(long id, InvestmentTransactionCommand command);
}
