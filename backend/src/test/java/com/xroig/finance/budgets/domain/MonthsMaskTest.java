package com.xroig.finance.budgets.domain;

import com.xroig.finance.shared.domain.ValidationException;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Domain tests for {@link MonthsMask}: the 12-bit mask behavior and its month-range validation. */
class MonthsMaskTest {

    @Test
    void ofMonths_buildsTheBitmaskAndReadsBackTheMonths() {
        MonthsMask mask = MonthsMask.ofMonths(List.of(1, 12));

        assertThat(mask.bitmask()).isEqualTo(0b1000_0000_0001);
        assertThat(mask.appliesToMonth(1)).isTrue();
        assertThat(mask.appliesToMonth(12)).isTrue();
        assertThat(mask.appliesToMonth(2)).isFalse();
        assertThat(mask.toMonths()).containsExactly(1, 12);
    }

    @Test
    void toMonths_isSortedRegardlessOfInputOrder() {
        assertThat(MonthsMask.ofMonths(List.of(6, 1, 3)).toMonths()).containsExactly(1, 3, 6);
    }

    @Test
    void ofBitmask_isTheInverseOfOfMonths() {
        MonthsMask mask = MonthsMask.ofBitmask(0b101); // months 1 and 3
        assertThat(mask.toMonths()).containsExactly(1, 3);
    }

    @Test
    void ofMonths_rejectsMonthsOutOfRange() {
        assertThatThrownBy(() -> MonthsMask.ofMonths(List.of(0))).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> MonthsMask.ofMonths(List.of(13))).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> MonthsMask.ofMonths(Arrays.asList((Integer) null)))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void equalsByValue() {
        assertThat(MonthsMask.ofMonths(List.of(1, 3))).isEqualTo(MonthsMask.ofBitmask(0b101));
    }
}
