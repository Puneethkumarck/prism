package com.stablebridge.prism.infrastructure.console;

import static com.stablebridge.prism.fixtures.TransactionFixtures.SOME_FAILED_TRANSACTION;
import static com.stablebridge.prism.fixtures.TransactionFixtures.SOME_LARGE_TRANSFER;
import static com.stablebridge.prism.fixtures.TransactionFixtures.SOME_MEMO_TRANSACTION;
import static com.stablebridge.prism.fixtures.TransactionFixtures.SOME_TRANSACTION;
import static com.stablebridge.prism.fixtures.TransactionFixtures.transactionBuilder;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.stablebridge.prism.domain.model.Signature;

class ConsoleOutputFormatterTest {

    private ByteArrayOutputStream buffer;
    private PrintStream stream;

    @BeforeEach
    void setUp() {
        buffer = new ByteArrayOutputStream();
        stream = new PrintStream(buffer, true, UTF_8);
    }

    @Test
    void shouldPrintSlotAlwaysRegardlessOfToggle() {
        // given
        var formatter = new ConsoleOutputFormatter(false, stream);

        // when
        formatter.printSlot(280_000_000L, 279_999_999L);

        // then
        assertThat(output())
                .contains("[SLOT]")
                .contains("Slot: 280000000")
                .contains("Parent: 279999999")
                .contains(ConsoleOutputFormatter.CYAN_BOLD);
    }

    @Test
    void shouldSuppressTransactionOutputWhenToggleOff() {
        // given
        var formatter = new ConsoleOutputFormatter(false, stream);

        // when
        formatter.printTransaction(SOME_TRANSACTION);

        // then
        assertThat(output()).isEmpty();
    }

    @Test
    void shouldPrintFailedInRed() {
        // given
        var formatter = new ConsoleOutputFormatter(true, stream);

        // when
        formatter.printTransaction(SOME_FAILED_TRANSACTION);

        // then
        assertThat(output())
                .contains("[TX]")
                .contains(ConsoleOutputFormatter.RED)
                .doesNotContain("[MEMO]")
                .doesNotContain("[TRANSFER]");
    }

    @Test
    void shouldPrintMemoInMagenta() {
        // given
        var formatter = new ConsoleOutputFormatter(true, stream);

        // when
        formatter.printTransaction(SOME_MEMO_TRANSACTION);

        // then
        assertThat(output())
                .contains("[MEMO]")
                .contains("hello solana")
                .contains(ConsoleOutputFormatter.MAGENTA);
    }

    @Test
    void shouldPrintTransferInYellow() {
        // given
        var formatter = new ConsoleOutputFormatter(true, stream);

        // when
        formatter.printTransaction(SOME_LARGE_TRANSFER);

        // then
        assertThat(output())
                .contains("[TRANSFER]")
                .contains(ConsoleOutputFormatter.YELLOW)
                .contains("Amount: 5.0 SOL")
                .doesNotContain("[MEMO]");
    }

    @Test
    void shouldPrintNormalInWhite() {
        // given
        var formatter = new ConsoleOutputFormatter(true, stream);

        // when
        formatter.printTransaction(SOME_TRANSACTION);

        // then
        assertThat(output())
                .contains("[TX]")
                .contains(ConsoleOutputFormatter.WHITE)
                .doesNotContain("[MEMO]")
                .doesNotContain("[TRANSFER]");
    }

    @Test
    void shouldPrintBothMemoAndTransfer() {
        // given
        var tx = transactionBuilder()
                .signature(new Signature("5Kx7aEwMbMemoAndTransfer9"))
                .amount(new BigDecimal("5.0"))
                .memo("payment note")
                .build();
        var formatter = new ConsoleOutputFormatter(true, stream);

        // when
        formatter.printTransaction(tx);

        // then
        var output = output();
        assertThat(output).contains("[MEMO]");
        assertThat(output).contains("payment note");
        assertThat(output).contains("[TRANSFER]");
        assertThat(output).contains(ConsoleOutputFormatter.MAGENTA);
        assertThat(output).contains(ConsoleOutputFormatter.YELLOW);
    }

    @Test
    void shouldTruncateSignatureLongerThan20Chars() {
        // given
        var longValue = "abcdefgh0123456789XYZlongerSignature";
        var tx = transactionBuilder()
                .signature(new Signature(longValue))
                .failed(true)
                .build();
        var formatter = new ConsoleOutputFormatter(true, stream);

        // when
        formatter.printTransaction(tx);

        // then
        assertThat(output())
                .contains("abcdefgh...ignature")
                .doesNotContain(longValue);
    }

    @Test
    void shouldNotTruncateSignatureOfExactly20Chars() {
        // given
        var shortValue = "12345678901234567890";
        var tx = transactionBuilder()
                .signature(new Signature(shortValue))
                .failed(true)
                .build();
        var formatter = new ConsoleOutputFormatter(true, stream);

        // when
        formatter.printTransaction(tx);

        // then
        assertThat(output()).contains(shortValue);
    }

    @Test
    void shouldRejectNullPrintStream() {
        // when/then
        assertThatThrownBy(() -> new ConsoleOutputFormatter(true, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("out");
    }

    @Test
    void shouldRejectNullTransaction() {
        // given
        var formatter = new ConsoleOutputFormatter(true, stream);

        // when/then
        assertThatThrownBy(() -> formatter.printTransaction(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("tx");
    }

    private String output() {
        return buffer.toString(UTF_8);
    }
}
