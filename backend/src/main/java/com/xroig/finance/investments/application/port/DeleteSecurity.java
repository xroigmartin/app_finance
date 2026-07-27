package com.xroig.finance.investments.application.port;

/** Inbound port: delete an instrument without operations (guard RN-5 → 409). */
public interface DeleteSecurity {

    void delete(long id);
}
