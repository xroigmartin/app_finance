package com.xroig.finance.investments.domain;

/**
 * One unreadable/unsupported row or non-blocking incident of a persisted
 * {@link ImportRecord} (section + row reference + reason). Same shape as
 * {@code application.FlexRowError}, duplicated here rather than reused because
 * the domain does not depend upward on {@code application} — the mapping happens
 * in {@code FlexImportService} when it builds the {@link ImportRecord}.
 */
public record ImportRowIssue(String section, String reference, String message) {
}
