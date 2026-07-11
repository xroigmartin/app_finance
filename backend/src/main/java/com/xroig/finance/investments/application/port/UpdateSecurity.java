package com.xroig.finance.investments.application.port;

import com.xroig.finance.investments.domain.Security;

/**
 * Inbound port: edit an instrument's non-identity metadata (name, ticker, type,
 * exchange, figi). The ISIN+currency identity is never editable (RN-9).
 */
public interface UpdateSecurity {

    Security update(long id, UpdateSecurityCommand command);

    /** Intent to edit an instrument's metadata, in the domain's own terms. */
    record UpdateSecurityCommand(String name, String ticker, String type, String exchange, String figi) {
    }
}
