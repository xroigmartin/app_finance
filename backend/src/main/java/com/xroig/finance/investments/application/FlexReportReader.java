package com.xroig.finance.investments.application;

import org.springframework.web.multipart.MultipartFile;

/**
 * Outbound port (anti-corruption layer): turns an uploaded IBKR Flex Query XML
 * into a {@link FlexReport} in the domain's language. Format/IO problems surface
 * as a domain {@code ValidationException} (→ 400); unsupported rows are reported
 * per row inside the result, never aborting the rest (§8). Implemented by the
 * parser adapter.
 */
public interface FlexReportReader {

    FlexReport read(MultipartFile file);
}
