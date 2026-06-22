package com.xroig.finance.shared.domain;

import java.time.LocalDate;
import java.util.Objects;

/**
 * An inclusive {@code [from, to]} range of dates. A value object the query side
 * uses to express periods; rejects an inverted range at construction.
 */
public record DateRange(LocalDate from, LocalDate to) {

    public DateRange {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        if (to.isBefore(from)) {
            throw new ValidationException("El rango de fechas es inválido: 'hasta' (" + to
                    + ") es anterior a 'desde' (" + from + ").");
        }
    }

    public boolean contains(LocalDate date) {
        return !date.isBefore(from) && !date.isAfter(to);
    }
}
