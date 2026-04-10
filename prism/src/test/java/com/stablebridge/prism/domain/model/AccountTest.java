package com.stablebridge.prism.domain.model;

import static com.stablebridge.prism.fixtures.AccountFixtures.SOME_ACCOUNT;
import static com.stablebridge.prism.fixtures.AccountFixtures.accountBuilder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class AccountTest {

    @Test
    void shouldBuildViaBuilder() {
        // given
        var pk = new Pubkey("pk1");
        var expected = Account.builder()
                .pubkey(pk)
                .lamports(500_000_000L)
                .slot(100L)
                .executable(false)
                .rentEpoch(0)
                .build();

        // when
        var result = Account.builder()
                .pubkey(pk)
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

    @Test
    void shouldRejectNullPubkey() {
        // when/then
        assertThatThrownBy(() -> accountBuilder().pubkey(null).build())
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("pubkey");
    }

    @Test
    void shouldRejectNegativeLamports() {
        // when/then
        assertThatThrownBy(() -> accountBuilder().lamports(-1).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("lamports");
    }

    @Test
    void shouldRejectNegativeSlot() {
        // when/then
        assertThatThrownBy(() -> accountBuilder().slot(-1).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("slot");
    }

    @Test
    void shouldRejectNegativeRentEpoch() {
        // when/then
        assertThatThrownBy(() -> accountBuilder().rentEpoch(-1).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("rentEpoch");
    }
}
