package com.xroig.finance.investments.domain;

import java.util.List;
import java.util.Map;

/**
 * Result of {@link PositionCalculator}: the computed positions per security
 * (RN-3), the portfolio cash per currency as direct sums of signed amounts (RN-2)
 * and the non-blocking warnings collected along the way (RN-4, missing rates).
 */
public record PortfolioPositions(List<Position> positions,
                                 Map<String, CurrencyMoney> cashByCurrency,
                                 List<PositionWarning> warnings) {
}
