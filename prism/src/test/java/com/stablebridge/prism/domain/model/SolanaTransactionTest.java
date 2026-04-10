package com.stablebridge.prism.domain.model;

import static com.stablebridge.prism.fixtures.TransactionFixtures.SOME_FAILED_TRANSACTION;
import static com.stablebridge.prism.fixtures.TransactionFixtures.SOME_LARGE_TRANSFER;
import static com.stablebridge.prism.fixtures.TransactionFixtures.SOME_MEMO_TRANSACTION;
import static com.stablebridge.prism.fixtures.TransactionFixtures.SOME_TRANSACTION;
import static com.stablebridge.prism.fixtures.TransactionFixtures.transactionBuilder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class SolanaTransactionTest {

    @Test
    void shouldBuildViaBuilder() {
        // given
        var sig = new Signature("sig1");
        var expected = SolanaTransaction.builder()
                .signature(sig)
                .slot(100L)
                .amount(new BigDecimal("1.5"))
                .failed(false)
                .memo("test memo")
                .from(new Pubkey("sender"))
                .to(new Pubkey("receiver"))
                .build();

        // when
        var result = SolanaTransaction.builder()
                .signature(sig)
                .slot(100L)
                .amount(new BigDecimal("1.5"))
                .failed(false)
                .memo("test memo")
                .from(new Pubkey("sender"))
                .to(new Pubkey("receiver"))
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
        // given
        var original = SOME_TRANSACTION;

        // when
        var result = original.toBuilder()
                .memo(null)
                .from(null)
                .to(null)
                .build();

        // then
        var expected = original.toBuilder()
                .memo(null)
                .from(null)
                .to(null)
                .build();
        assertThat(result).usingRecursiveComparison().isEqualTo(expected);
    }

    @Test
    void shouldRejectNullSignature() {
        // when/then
        assertThatThrownBy(() -> transactionBuilder().signature(null).build())
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("signature");
    }

    @Test
    void shouldRejectNullAmount() {
        // when/then
        assertThatThrownBy(() -> transactionBuilder().amount(null).build())
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("amount");
    }

    @Test
    void shouldRejectNegativeSlot() {
        // when/then
        assertThatThrownBy(() -> transactionBuilder().slot(-1).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("slot");
    }

    @Test
    void shouldCreateLargeTransfer() {
        // given
        var tx = SOME_LARGE_TRANSFER;

        // when
        var result = tx.toLargeTransfer();

        // then
        var expected = LargeTransfer.builder()
                .signature(tx.signature())
                .slot(tx.slot())
                .amount(tx.amount())
                .build();
        assertThat(result).usingRecursiveComparison().isEqualTo(expected);
    }

    @Test
    void shouldCreateMemo() {
        // given
        var tx = SOME_MEMO_TRANSACTION;

        // when
        var result = tx.toMemo();

        // then
        var expected = Memo.builder()
                .signature(tx.signature())
                .memoText(tx.memo())
                .build();
        assertThat(result).usingRecursiveComparison().isEqualTo(expected);
    }

    @Test
    void shouldCreateFailedTransaction() {
        // given
        var tx = SOME_FAILED_TRANSACTION;

        // when
        var result = tx.toFailedTransaction("InstructionError");

        // then
        var expected = FailedTransaction.builder()
                .signature(tx.signature())
                .slot(tx.slot())
                .error("InstructionError")
                .build();
        assertThat(result).usingRecursiveComparison().isEqualTo(expected);
    }
}
