/**
 * Imports bounded context — infrastructure bridge adapters. They implement the
 * imports outbound ports ({@code AccountDirectory}, {@code CategoryDirectory},
 * {@code RuleDirectory}, {@code MovementWriter}, {@code TransferWriter}) by delegating
 * to the application APIs of the already-migrated contexts (accounts, categories,
 * categorization, transactions, transfers) — so the import reuses their use cases and
 * read models instead of touching persistence directly.
 */
package com.xroig.finance.imports.infrastructure.bridge;
