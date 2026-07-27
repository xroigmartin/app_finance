package com.xroig.finance.investments.application.port;

import com.xroig.finance.investments.application.FlexImportResult;
import org.springframework.web.multipart.MultipartFile;

/**
 * Inbound port: import an IBKR Activity Flex XML into a portfolio (RF-3). The
 * account's base currency must match the portfolio's or the whole import is
 * rejected (§8); rows already imported are skipped by {@code external_id}
 * (RN-10); securities are auto-registered and their metadata refreshed, and
 * quotes/rates upserted (RN-9).
 */
public interface ImportFlexReport {

    FlexImportResult importReport(long portfolioId, MultipartFile file);
}
