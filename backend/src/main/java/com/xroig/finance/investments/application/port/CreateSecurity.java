package com.xroig.finance.investments.application.port;

import com.xroig.finance.investments.domain.Security;

/** Inbound port: register an instrument in the catalogue (also used by the import's auto-registration). */
public interface CreateSecurity {

    Security create(CreateSecurityCommand command);

    /** Intent to register an instrument, in the domain's own terms. */
    record CreateSecurityCommand(String isin, String currency, String name,
                                 String ticker, String type, String exchange, String figi) {
    }
}
