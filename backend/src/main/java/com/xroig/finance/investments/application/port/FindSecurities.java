package com.xroig.finance.investments.application.port;

import com.xroig.finance.investments.domain.Security;

import java.util.List;

/** Inbound port: list the instrument catalogue. */
public interface FindSecurities {

    List<Security> all();
}
