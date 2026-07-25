/**
 * Imports bounded context — domain layer. The import is mostly orchestration (an
 * anti-corruption layer over bank files), so its domain is thin: {@link
 * com.xroig.finance.imports.domain.ImportRow} (the language of a parsed file row,
 * with the bank amount/date formats) plus the outbound ports the use case drives —
 * {@link com.xroig.finance.imports.domain.AccountDirectory}, {@link
 * com.xroig.finance.imports.domain.CategoryDirectory}, {@link
 * com.xroig.finance.imports.domain.RuleDirectory}, {@link
 * com.xroig.finance.imports.domain.MovementWriter} and {@link
 * com.xroig.finance.imports.domain.TransferWriter}. Pure of Spring/JPA; the adapters
 * bridge them to the already-migrated contexts.
 */
package com.xroig.finance.imports.domain;
