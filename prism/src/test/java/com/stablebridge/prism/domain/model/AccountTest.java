package com.stablebridge.prism.domain.model;

import static com.stablebridge.prism.fixtures.AccountFixtures.SOME_ACCOUNT;
import static com.stablebridge.prism.fixtures.AccountFixtures.accountBuilder;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AccountTest {

    @Test
    void shouldBuildViaBuilder() {
        // given
        var expected = Account.builder()
                .pubkey("pk1")
                .lamports(500_000_000L)
                .slot(100L)
                .executable(false)
                .rentEpoch(0)
                .build();

        // when
        var result = Account.builder()
                .pubkey("pk1")
                .lamports(500_000_000L)
                .slot(100L)
                .executable(false)
                .rentEpoch(0)
                .build();

        // then
        assertThat(result).usingRecursiveComparison().isEqualTo(expected);
    }

    @Test
    void shouldCreateCopyViaToBuilder() {
        // given
        var original = accountBuilder().build();

        // when
        var copy = original.toBuilder().build();

        // then
        assertThat(copy).usingRecursiveComparison().isEqualTo(original);
    }

    @Test
    void shouldModifyFieldViaToBuilder() {
        // given
        var original = SOME_ACCOUNT;

        // when
        var modified = original.toBuilder().executable(true).build();

        // then
        var expected = original.toBuilder().executable(true).build();
        assertThat(modified).usingRecursiveComparison().isEqualTo(expected);
    }
}
