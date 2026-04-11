package com.stablebridge.prism.infrastructure.console;

import java.io.PrintStream;
import java.util.Objects;

import com.stablebridge.prism.domain.model.SolanaTransaction;
import com.stablebridge.prism.domain.service.LargeTransferFilter;

public class ConsoleOutputFormatter {

    static final String RESET = "\u001B[0m";
    static final String RED = "\u001B[31m";
    static final String MAGENTA = "\u001B[35m";
    static final String YELLOW = "\u001B[33m";
    static final String WHITE = "\u001B[37m";
    static final String CYAN_BOLD = "\u001B[1;36m";

    private static final int SIGNATURE_TRUNCATE_THRESHOLD = 20;
    private static final int SIGNATURE_PREFIX_LENGTH = 8;
    private static final int SIGNATURE_SUFFIX_LENGTH = 8;

    private final boolean consoleLog;
    private final PrintStream out;

    public ConsoleOutputFormatter(boolean consoleLog) {
        this(consoleLog, System.out);
    }

    ConsoleOutputFormatter(boolean consoleLog, PrintStream out) {
        this.consoleLog = consoleLog;
        this.out = Objects.requireNonNull(out, "out must not be null");
    }

    public void printSlot(long slot, long parentSlot) {
        out.println(CYAN_BOLD + "[SLOT]" + RESET + " Slot: " + slot + " (Parent: " + parentSlot + ")");
    }

    public void printTransaction(SolanaTransaction tx) {
        Objects.requireNonNull(tx, "tx must not be null");
        if (!consoleLog) {
            return;
        }

        var signature = truncateSignature(tx.signature().value());

        if (tx.failed()) {
            out.println(RED + "[TX]" + RESET + " " + signature + " " + tx.amount() + " SOL");
            return;
        }

        var hasMemo = tx.memo() != null;
        var isLarge = LargeTransferFilter.isLargeTransfer(tx.amount());

        if (hasMemo) {
            out.println(MAGENTA + "[MEMO]" + RESET + " " + tx.memo());
        }
        if (isLarge) {
            out.println(YELLOW + "[TRANSFER]" + RESET
                    + " From: " + addressValue(tx.from() != null ? tx.from().value() : null)
                    + " To: " + addressValue(tx.to() != null ? tx.to().value() : null)
                    + " Amount: " + tx.amount() + " SOL");
        }
        if (!hasMemo && !isLarge) {
            out.println(WHITE + "[TX]" + RESET + " " + signature + " " + tx.amount() + " SOL");
        }
    }

    static String truncateSignature(String signature) {
        if (signature.length() <= SIGNATURE_TRUNCATE_THRESHOLD) {
            return signature;
        }
        return signature.substring(0, SIGNATURE_PREFIX_LENGTH)
                + "..."
                + signature.substring(signature.length() - SIGNATURE_SUFFIX_LENGTH);
    }

    private static String addressValue(String value) {
        return value != null ? value : "?";
    }
}
