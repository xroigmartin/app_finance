package com.xroig.finance.investments.application;

/**
 * Instrument reference of a parsed Flex row, in the report's own terms: the
 * ISIN+currency business identity plus the non-identity metadata (RN-9) the
 * import uses to auto-register the {@code Security} and refresh it on reimport.
 */
public record FlexInstrument(String isin, String currency, String name,
                             String ticker, String exchange, String figi) {
}
