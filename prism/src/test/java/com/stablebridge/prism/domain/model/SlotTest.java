package com.stablebridge.prism.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SlotTest {

    @Test
    void shouldBuildViaBuilder() {
        // given
        var expected = Slot.builder().value(280_000_000L).build();

        // when
        var result = Slot.builder().value(280_000_000L).build();

        // then
        assertThat(result).usingRecursiveComparison().isEqualTo(expected);
    }

    @Test
    void shouldCreateCopyViaToBuilder() {
        // given
        var original = Slot.builder().value(280_000_000L).build();

        // when
        var copy = original.toBuilder().build();

        // then
        assertThat(copy).usingRecursiveComparison().isEqualTo(original);
    }
}
