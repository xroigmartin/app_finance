package com.xroig.finance.accounts.infrastructure.web;

import com.xroig.finance.accounts.domain.Account;

import java.math.BigDecimal;

/** Outbound web DTO: the JSON shape of an account ({@code id, name, type, initialBalance}). */
public record AccountResponse(Long id, String name, String type, BigDecimal initialBalance) {

    /** The controller only ever serializes persisted accounts, so the identity is always present. */
    public static AccountResponse from(Account account) {
        return new AccountResponse(
                account.id().value(),
                account.name(),
                account.type(),
                account.initialBalance().amount());
    }
}
