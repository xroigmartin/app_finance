package com.xroig.finance.shared.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PageTest {

    @Test
    void totalPages_roundsUpTheRemainder() {
        Page<String> page = new Page<>(List.of("a", "b"), 0, 5, 11);

        assertThat(page.totalPages()).isEqualTo(3);
    }

    @Test
    void totalPages_exactDivision_hasNoExtraPage() {
        Page<String> page = new Page<>(List.of(), 0, 5, 10);

        assertThat(page.totalPages()).isEqualTo(2);
    }

    @Test
    void totalPages_noElements_isZero() {
        Page<String> page = new Page<>(List.of(), 0, 10, 0);

        assertThat(page.totalPages()).isZero();
    }

    @Test
    void accessors_exposeContentPageSizeAndTotalElements() {
        List<String> content = List.of("x", "y");
        Page<String> page = new Page<>(content, 2, 25, 53);

        assertThat(page.content()).isEqualTo(content);
        assertThat(page.page()).isEqualTo(2);
        assertThat(page.size()).isEqualTo(25);
        assertThat(page.totalElements()).isEqualTo(53);
    }
}
