package com.xroig.finance.investments.application.port;

import com.xroig.finance.investments.application.PriceRefreshResult;

/** Inbound port: refresh the catalogue's prices on demand against the external provider. */
public interface RefreshPrices {

    PriceRefreshResult refresh();
}
