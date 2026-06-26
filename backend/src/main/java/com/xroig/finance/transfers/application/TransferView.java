package com.xroig.finance.transfers.application;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Read model (CQRS) for a transfer as the API exposes it: the flat fields plus the
 * nested {@code fromAccount}/{@code toAccount}. Assembled by the read adapter from the
 * persistence graph; faithful to the legacy JSON for the fields the UI consumes.
 */
public record TransferView(Long id, LocalDate date, BigDecimal amount, String description,
                           AccountRef fromAccount, AccountRef toAccount) {

    public record AccountRef(Long id, String name, String type, BigDecimal initialBalance) {
    }
}
