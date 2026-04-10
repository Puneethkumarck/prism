package com.stablebridge.prism.domain.model;

import static com.stablebridge.prism.fixtures.TransactionFixtures.SOME_TRANSACTION;
import static com.stablebridge.prism.fixtures.TransactionFixtures.transactionBuilder;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SolanaTransactionTest {

    @Test
    void shouldBuildViaBuilder() {
        // given
        var expected = SolanaTransaction.builder()
                .signature("sig1")
                .slot(100L)
                .amount(1.5)
                .failed(false)
                .memo("test memo")
                .from("sender")
                .to("receiver")
                .build();

        // when
        var result = SolanaTransaction.builder()
                .signature("sig1")
                .slot(100L)
                .amount(1.5)
                .failed(false)
                .memo("test memo")
                .from("sender")
                .to("receiver")
                .build();

        // then
        assertThat(result).usingRecursiveComparison().isEqualTo(expected);
    }

    @Test
    void shouldCreateCopyViaToBuilder() {
        // given
        var original = transactionBuilder().build();

        // when
        var copy = original.toBuilder().build();

        // then
        assertThat(copy).usingRecursiveComparison().isEqualTo(original);
    }

    @Test
    void shouldModifyFieldViaToBuilder() {
        // given
        var original = SOME_TRANSACTION;

        // when
        var modified = original.toBuilder().failed(true).build();

        // then
        var expected = original.toBuilder().failed(true).build();
        assertThat(modified).usingRecursiveComparison().isEqualTo(expected);
    }

    @Test
    void shouldAllowNullableFields() {
        // given / when
        var result = transactionBuilder()
                .memo(null)
                .from(null)
                .to(null)
                .build();

        // then
        var expected = transactionBuilder()
                .memo(null)
                .from(null)
                .to(null)
                .build();
        assertThat(result).usingRecursiveComparison().isEqualTo(expected);
    }
}
