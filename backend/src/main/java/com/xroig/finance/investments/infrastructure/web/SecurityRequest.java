package com.xroig.finance.investments.infrastructure.web;

import jakarta.validation.constraints.NotBlank;

/**
 * Inbound web DTO for registering/editing an instrument. Only the name is
 * validated at the boundary: ISIN and currency are required on creation by the
 * domain itself (→ 400) and ignored on update — the ISIN+currency identity is
 * never editable (RN-9), so editing touches only the non-identity metadata.
 */
public record SecurityRequest(String isin,
                              String currency,
                              @NotBlank String name,
                              String ticker,
                              String type,
                              String exchange,
                              String figi) {
}
