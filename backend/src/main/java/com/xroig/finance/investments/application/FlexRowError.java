package com.xroig.finance.investments.application;

/**
 * One unreadable or unsupported row of the Flex report (§8): the parser reports
 * it (section + row reference + reason) and keeps processing the rest — a partial
 * report never aborts the import.
 */
public record FlexRowError(String section, String reference, String message) {
}
