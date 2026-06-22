package com.xroig.finance.shared.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the {@link DateRange} value object (stage H0a): inclusive
 * containment and rejection of an inverted range as a {@link ValidationException}.
 */
class DateRangeTest {

    private static final LocalDate JAN = LocalDate.of(2024, 1, 1);
    private static final LocalDate DEC = LocalDate.of(2024, 12, 31);

    @Test
    void containsIsInclusiveOnBothEnds() {
        DateRange range = new DateRange(JAN, DEC);
        assertThat(range.contains(JAN)).isTrue();
        assertThat(range.contains(DEC)).isTrue();
        assertThat(range.contains(LocalDate.of(2024, 6, 15))).isTrue();
        assertThat(range.contains(LocalDate.of(2023, 12, 31))).isFalse();
        assertThat(range.contains(LocalDate.of(2025, 1, 1))).isFalse();
    }

    @Test
    void invertedRangeIsRejected() {
        assertThatThrownBy(() -> new DateRange(DEC, JAN))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("anterior a 'desde'");
    }

    @Test
    void singleDayRangeIsValid() {
        DateRange oneDay = new DateRange(JAN, JAN);
        assertThat(oneDay.contains(JAN)).isTrue();
    }
}
