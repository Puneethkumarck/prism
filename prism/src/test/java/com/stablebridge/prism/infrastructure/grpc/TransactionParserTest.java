package com.stablebridge.prism.infrastructure.grpc;

import static com.stablebridge.prism.fixtures.GeyserTestFixtures.MEMO_V1_PROGRAM_ID_BYTES;
import static com.stablebridge.prism.fixtures.GeyserTestFixtures.MEMO_V2_PROGRAM_ID_BYTES;
import static com.stablebridge.prism.fixtures.GeyserTestFixtures.SOME_FEE_PAYER_PUBKEY_BASE58;
import static com.stablebridge.prism.fixtures.GeyserTestFixtures.SOME_FEE_PAYER_PUBKEY_BYTES;
import static com.stablebridge.prism.fixtures.GeyserTestFixtures.SOME_RECEIVER_PUBKEY_BASE58;
import static com.stablebridge.prism.fixtures.GeyserTestFixtures.SOME_RECEIVER_PUBKEY_BYTES;
import static com.stablebridge.prism.fixtures.GeyserTestFixtures.SOME_SENDER_PUBKEY_BASE58;
import static com.stablebridge.prism.fixtures.GeyserTestFixtures.SOME_SENDER_PUBKEY_BYTES;
import static com.stablebridge.prism.fixtures.GeyserTestFixtures.SOME_SIGNATURE_BASE58;
import static com.stablebridge.prism.fixtures.GeyserTestFixtures.SOME_SIGNATURE_BYTES;
import static com.stablebridge.prism.fixtures.GeyserTestFixtures.failedTxWithBalances;
import static com.stablebridge.prism.fixtures.GeyserTestFixtures.txWithBalances;
import static com.stablebridge.prism.fixtures.GeyserTestFixtures.txWithInnerMemo;
import static com.stablebridge.prism.fixtures.GeyserTestFixtures.txWithTopLevelMemo;
import static com.stablebridge.prism.fixtures.GeyserTestFixtures.txWithoutInnerTransaction;
import static com.stablebridge.prism.fixtures.GeyserTestFixtures.txWithoutMessage;
import static com.stablebridge.prism.fixtures.GeyserTestFixtures.txWithoutMeta;
import static com.stablebridge.prism.fixtures.GeyserTestFixtures.updateWithoutTransaction;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.stablebridge.prism.domain.model.Account;
import com.stablebridge.prism.domain.model.Pubkey;
import com.stablebridge.prism.domain.model.Signature;
import com.stablebridge.prism.domain.model.SolanaTransaction;
import com.stablebridge.prism.domain.solana.SolanaAddress;

class TransactionParserTest {

    private static final long SOME_SLOT = 280_000_000L;
    private static final long FIVE_SOL_LAMPORTS = 5_000_000_000L;

    private final TransactionParser parser = new TransactionParser();

    @Test
    void shouldEncodeSignatureAsBase58() {
        // given
        var tx = txWithBalances(
                SOME_SLOT,
                SOME_SIGNATURE_BYTES,
                List.of(SOME_SENDER_PUBKEY_BYTES, SOME_RECEIVER_PUBKEY_BYTES),
                List.of(FIVE_SOL_LAMPORTS, 0L),
                List.of(0L, FIVE_SOL_LAMPORTS));

        // when
        var result = parser.parseTransaction(tx);

        // then
        var expected = SolanaTransaction.builder()
                .signature(new Signature(SOME_SIGNATURE_BASE58))
                .slot(SOME_SLOT)
                .amount(BigDecimal.valueOf(FIVE_SOL_LAMPORTS, 9))
                .failed(false)
                .memo(null)
                .from(new Pubkey(SolanaAddress.truncate(SOME_SENDER_PUBKEY_BASE58)))
                .to(new Pubkey(SolanaAddress.truncate(SOME_RECEIVER_PUBKEY_BASE58)))
                .build();
        assertThat(result).isPresent();
        assertThat(result.get()).usingRecursiveComparison().isEqualTo(expected);
    }

    @Test
    void shouldComputeAmountFromBalanceDifferentials() {
        // given
        var tx = txWithBalances(
                SOME_SLOT,
                SOME_SIGNATURE_BYTES,
                List.of(SOME_SENDER_PUBKEY_BYTES, SOME_RECEIVER_PUBKEY_BYTES),
                List.of(7_500_000_000L, 0L),
                List.of(2_500_000_000L, 5_000_000_000L));

        // when
        var result = parser.parseTransaction(tx);

        // then
        var expected = SolanaTransaction.builder()
                .signature(new Signature(SOME_SIGNATURE_BASE58))
                .slot(SOME_SLOT)
                .amount(BigDecimal.valueOf(FIVE_SOL_LAMPORTS, 9))
                .failed(false)
                .memo(null)
                .from(new Pubkey(SolanaAddress.truncate(SOME_SENDER_PUBKEY_BASE58)))
                .to(new Pubkey(SolanaAddress.truncate(SOME_RECEIVER_PUBKEY_BASE58)))
                .build();
        assertThat(result).isPresent();
        assertThat(result.get()).usingRecursiveComparison().isEqualTo(expected);
    }

    @Test
    void shouldIdentifySenderAsMaxDecrease() {
        // given
        var tx = txWithBalances(
                SOME_SLOT,
                SOME_SIGNATURE_BYTES,
                List.of(SOME_FEE_PAYER_PUBKEY_BYTES, SOME_SENDER_PUBKEY_BYTES, SOME_RECEIVER_PUBKEY_BYTES),
                List.of(1_000_000_000L, 10_000_000_000L, 0L),
                List.of(999_995_000L, 4_000_000_000L, 6_000_000_000L));

        // when
        var result = parser.parseTransaction(tx);

        // then
        var expected = SolanaTransaction.builder()
                .signature(new Signature(SOME_SIGNATURE_BASE58))
                .slot(SOME_SLOT)
                .amount(BigDecimal.valueOf(6_000_000_000L, 9))
                .failed(false)
                .memo(null)
                .from(new Pubkey(SolanaAddress.truncate(SOME_SENDER_PUBKEY_BASE58)))
                .to(new Pubkey(SolanaAddress.truncate(SOME_RECEIVER_PUBKEY_BASE58)))
                .build();
        assertThat(result).isPresent();
        assertThat(result.get()).usingRecursiveComparison().isEqualTo(expected);
    }

    @Test
    void shouldIdentifyReceiverAsMaxIncrease() {
        // given
        var tx = txWithBalances(
                SOME_SLOT,
                SOME_SIGNATURE_BYTES,
                List.of(SOME_FEE_PAYER_PUBKEY_BYTES, SOME_SENDER_PUBKEY_BYTES, SOME_RECEIVER_PUBKEY_BYTES),
                List.of(1_000_000_000L, 10_000_000_000L, 500_000_000L),
                List.of(999_995_000L, 4_000_000_000L, 6_500_000_000L));

        // when
        var result = parser.parseTransaction(tx);

        // then
        var expected = SolanaTransaction.builder()
                .signature(new Signature(SOME_SIGNATURE_BASE58))
                .slot(SOME_SLOT)
                .amount(BigDecimal.valueOf(6_000_000_000L, 9))
                .failed(false)
                .memo(null)
                .from(new Pubkey(SolanaAddress.truncate(SOME_SENDER_PUBKEY_BASE58)))
                .to(new Pubkey(SolanaAddress.truncate(SOME_RECEIVER_PUBKEY_BASE58)))
                .build();
        assertThat(result).isPresent();
        assertThat(result.get()).usingRecursiveComparison().isEqualTo(expected);
    }

    @Test
    void shouldExtractMemoFromTopLevelInstructions() {
        // given
        var tx = txWithTopLevelMemo(
                SOME_SLOT,
                SOME_SIGNATURE_BYTES,
                List.of(SOME_SENDER_PUBKEY_BYTES, SOME_RECEIVER_PUBKEY_BYTES),
                List.of(FIVE_SOL_LAMPORTS, 0L),
                List.of(0L, FIVE_SOL_LAMPORTS),
                MEMO_V1_PROGRAM_ID_BYTES,
                "hello solana");

        // when
        var result = parser.parseTransaction(tx);

        // then
        var expected = SolanaTransaction.builder()
                .signature(new Signature(SOME_SIGNATURE_BASE58))
                .slot(SOME_SLOT)
                .amount(BigDecimal.valueOf(FIVE_SOL_LAMPORTS, 9))
                .failed(false)
                .memo("hello solana")
                .from(new Pubkey(SolanaAddress.truncate(SOME_SENDER_PUBKEY_BASE58)))
                .to(new Pubkey(SolanaAddress.truncate(SOME_RECEIVER_PUBKEY_BASE58)))
                .build();
        assertThat(result).isPresent();
        assertThat(result.get()).usingRecursiveComparison().isEqualTo(expected);
    }

    @Test
    void shouldExtractMemoFromInnerInstructions() {
        // given
        var tx = txWithInnerMemo(
                SOME_SLOT,
                SOME_SIGNATURE_BYTES,
                List.of(SOME_SENDER_PUBKEY_BYTES, SOME_RECEIVER_PUBKEY_BYTES),
                List.of(FIVE_SOL_LAMPORTS, 0L),
                List.of(0L, FIVE_SOL_LAMPORTS),
                MEMO_V1_PROGRAM_ID_BYTES,
                "deep memo");

        // when
        var result = parser.parseTransaction(tx);

        // then
        var expected = SolanaTransaction.builder()
                .signature(new Signature(SOME_SIGNATURE_BASE58))
                .slot(SOME_SLOT)
                .amount(BigDecimal.valueOf(FIVE_SOL_LAMPORTS, 9))
                .failed(false)
                .memo("deep memo")
                .from(new Pubkey(SolanaAddress.truncate(SOME_SENDER_PUBKEY_BASE58)))
                .to(new Pubkey(SolanaAddress.truncate(SOME_RECEIVER_PUBKEY_BASE58)))
                .build();
        assertThat(result).isPresent();
        assertThat(result.get()).usingRecursiveComparison().isEqualTo(expected);
    }

    @Test
    void shouldStripNullBytesFromMemo() {
        // given
        var tx = txWithTopLevelMemo(
                SOME_SLOT,
                SOME_SIGNATURE_BYTES,
                List.of(SOME_SENDER_PUBKEY_BYTES, SOME_RECEIVER_PUBKEY_BYTES),
                List.of(FIVE_SOL_LAMPORTS, 0L),
                List.of(0L, FIVE_SOL_LAMPORTS),
                MEMO_V1_PROGRAM_ID_BYTES,
                "\0hello\0world\0");

        // when
        var result = parser.parseTransaction(tx);

        // then
        var expected = SolanaTransaction.builder()
                .signature(new Signature(SOME_SIGNATURE_BASE58))
                .slot(SOME_SLOT)
                .amount(BigDecimal.valueOf(FIVE_SOL_LAMPORTS, 9))
                .failed(false)
                .memo("helloworld")
                .from(new Pubkey(SolanaAddress.truncate(SOME_SENDER_PUBKEY_BASE58)))
                .to(new Pubkey(SolanaAddress.truncate(SOME_RECEIVER_PUBKEY_BASE58)))
                .build();
        assertThat(result).isPresent();
        assertThat(result.get()).usingRecursiveComparison().isEqualTo(expected);
    }

    @Test
    void shouldReturnNullMemoWhenNone() {
        // given
        var tx = txWithBalances(
                SOME_SLOT,
                SOME_SIGNATURE_BYTES,
                List.of(SOME_SENDER_PUBKEY_BYTES, SOME_RECEIVER_PUBKEY_BYTES),
                List.of(FIVE_SOL_LAMPORTS, 0L),
                List.of(0L, FIVE_SOL_LAMPORTS));

        // when
        var result = parser.parseTransaction(tx);

        // then
        var expected = SolanaTransaction.builder()
                .signature(new Signature(SOME_SIGNATURE_BASE58))
                .slot(SOME_SLOT)
                .amount(BigDecimal.valueOf(FIVE_SOL_LAMPORTS, 9))
                .failed(false)
                .memo(null)
                .from(new Pubkey(SolanaAddress.truncate(SOME_SENDER_PUBKEY_BASE58)))
                .to(new Pubkey(SolanaAddress.truncate(SOME_RECEIVER_PUBKEY_BASE58)))
                .build();
        assertThat(result).isPresent();
        assertThat(result.get()).usingRecursiveComparison().isEqualTo(expected);
    }

    @Test
    void shouldMarkFailedWhenMetaErrPresent() {
        // given
        var tx = failedTxWithBalances(
                SOME_SLOT,
                SOME_SIGNATURE_BYTES,
                List.of(SOME_SENDER_PUBKEY_BYTES, SOME_RECEIVER_PUBKEY_BYTES),
                List.of(FIVE_SOL_LAMPORTS, 0L),
                List.of(0L, FIVE_SOL_LAMPORTS));

        // when
        var result = parser.parseTransaction(tx);

        // then
        var expected = SolanaTransaction.builder()
                .signature(new Signature(SOME_SIGNATURE_BASE58))
                .slot(SOME_SLOT)
                .amount(BigDecimal.valueOf(FIVE_SOL_LAMPORTS, 9))
                .failed(true)
                .memo(null)
                .from(new Pubkey(SolanaAddress.truncate(SOME_SENDER_PUBKEY_BASE58)))
                .to(new Pubkey(SolanaAddress.truncate(SOME_RECEIVER_PUBKEY_BASE58)))
                .build();
        assertThat(result).isPresent();
        assertThat(result.get()).usingRecursiveComparison().isEqualTo(expected);
    }

    @Test
    void shouldExtractFeePayer() {
        // given
        var tx = txWithBalances(
                SOME_SLOT,
                SOME_SIGNATURE_BYTES,
                List.of(SOME_FEE_PAYER_PUBKEY_BYTES, SOME_RECEIVER_PUBKEY_BYTES),
                List.of(1_000_000_000L, 0L),
                List.of(900_000_000L, 100_000_000L));

        // when
        var result = parser.extractFeePayer(tx);

        // then
        var expected = Account.builder()
                .pubkey(new Pubkey(SOME_FEE_PAYER_PUBKEY_BASE58))
                .lamports(900_000_000L)
                .slot(SOME_SLOT)
                .executable(false)
                .rentEpoch(0L)
                .build();
        assertThat(result).isPresent();
        assertThat(result.get()).usingRecursiveComparison().isEqualTo(expected);
    }

    @Test
    void shouldReturnEmptyWhenMetaMissing() {
        // given
        var tx = txWithoutMeta(SOME_SLOT, SOME_SIGNATURE_BYTES);

        // when
        var result = parser.parseTransaction(tx);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnEmptyWhenMessageMissing() {
        // given
        var tx = txWithoutMessage(SOME_SLOT, SOME_SIGNATURE_BYTES);

        // when
        var result = parser.parseTransaction(tx);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnEmptyWhenInnerTransactionMissing() {
        // given
        var tx = txWithoutInnerTransaction(SOME_SLOT, SOME_SIGNATURE_BYTES);

        // when
        var result = parser.parseTransaction(tx);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnEmptyWhenUpdateHasNoTransaction() {
        // given
        var tx = updateWithoutTransaction(SOME_SLOT);

        // when
        var result = parser.parseTransaction(tx);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    void shouldExtractMemoV2FromTopLevelInstructions() {
        // given
        var tx = txWithTopLevelMemo(
                SOME_SLOT,
                SOME_SIGNATURE_BYTES,
                List.of(SOME_SENDER_PUBKEY_BYTES, SOME_RECEIVER_PUBKEY_BYTES),
                List.of(FIVE_SOL_LAMPORTS, 0L),
                List.of(0L, FIVE_SOL_LAMPORTS),
                MEMO_V2_PROGRAM_ID_BYTES,
                "hello v2");

        // when
        var result = parser.parseTransaction(tx);

        // then
        var expected = SolanaTransaction.builder()
                .signature(new Signature(SOME_SIGNATURE_BASE58))
                .slot(SOME_SLOT)
                .amount(BigDecimal.valueOf(FIVE_SOL_LAMPORTS, 9))
                .failed(false)
                .memo("hello v2")
                .from(new Pubkey(SolanaAddress.truncate(SOME_SENDER_PUBKEY_BASE58)))
                .to(new Pubkey(SolanaAddress.truncate(SOME_RECEIVER_PUBKEY_BASE58)))
                .build();
        assertThat(result).isPresent();
        assertThat(result.get()).usingRecursiveComparison().isEqualTo(expected);
    }

    @Test
    void shouldExtractMemoV2FromInnerInstructions() {
        // given
        var tx = txWithInnerMemo(
                SOME_SLOT,
                SOME_SIGNATURE_BYTES,
                List.of(SOME_SENDER_PUBKEY_BYTES, SOME_RECEIVER_PUBKEY_BYTES),
                List.of(FIVE_SOL_LAMPORTS, 0L),
                List.of(0L, FIVE_SOL_LAMPORTS),
                MEMO_V2_PROGRAM_ID_BYTES,
                "deep v2");

        // when
        var result = parser.parseTransaction(tx);

        // then
        var expected = SolanaTransaction.builder()
                .signature(new Signature(SOME_SIGNATURE_BASE58))
                .slot(SOME_SLOT)
                .amount(BigDecimal.valueOf(FIVE_SOL_LAMPORTS, 9))
                .failed(false)
                .memo("deep v2")
                .from(new Pubkey(SolanaAddress.truncate(SOME_SENDER_PUBKEY_BASE58)))
                .to(new Pubkey(SolanaAddress.truncate(SOME_RECEIVER_PUBKEY_BASE58)))
                .build();
        assertThat(result).isPresent();
        assertThat(result.get()).usingRecursiveComparison().isEqualTo(expected);
    }
}
