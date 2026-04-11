package com.stablebridge.prism.fixtures;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.protobuf.ByteString;
import com.stablebridge.prism.domain.model.Account;
import com.stablebridge.prism.domain.model.Pubkey;
import com.stablebridge.prism.domain.model.Signature;
import com.stablebridge.prism.domain.model.SolanaTransaction;
import com.stablebridge.prism.domain.solana.SolanaAddress;
import com.stablebridge.prism.domain.solana.SolanaPrograms;
import com.stablebridge.prism.infrastructure.grpc.proto.geyser.SubscribeUpdateTransaction;
import com.stablebridge.prism.infrastructure.grpc.proto.geyser.SubscribeUpdateTransactionInfo;
import com.stablebridge.prism.infrastructure.grpc.proto.solana.CompiledInstruction;
import com.stablebridge.prism.infrastructure.grpc.proto.solana.Message;
import com.stablebridge.prism.infrastructure.grpc.proto.solana.Transaction;
import com.stablebridge.prism.infrastructure.grpc.proto.solana.TransactionError;
import com.stablebridge.prism.infrastructure.grpc.proto.solana.TransactionStatusMeta;
import com.stablebridge.prism.infrastructure.solana.Base58;

public final class E2eBlockFixture {

    public static final int LAMPORTS_PER_SOL_SCALE = 9;
    public static final long LAMPORTS_PER_SOL = 1_000_000_000L;

    public static final long SOME_SLOT = 280_000_042L;

    public static final byte[] SOME_LARGE_TRANSFER_SIGNATURE_BYTES = sigBytes((byte) 0xA1);
    public static final byte[] SOME_FAILED_TX_SIGNATURE_BYTES = sigBytes((byte) 0xB2);
    public static final byte[] SOME_MEMO_TX_SIGNATURE_BYTES = sigBytes((byte) 0xC3);

    public static final String SOME_LARGE_TRANSFER_SIGNATURE_BASE58 =
            Base58.encode(SOME_LARGE_TRANSFER_SIGNATURE_BYTES);
    public static final String SOME_FAILED_TX_SIGNATURE_BASE58 =
            Base58.encode(SOME_FAILED_TX_SIGNATURE_BYTES);
    public static final String SOME_MEMO_TX_SIGNATURE_BASE58 = Base58.encode(SOME_MEMO_TX_SIGNATURE_BYTES);

    public static final byte[] SOME_LARGE_TRANSFER_FEE_PAYER_BYTES = pubkeyBytes((byte) 0x41);
    public static final byte[] SOME_FAILED_TX_FEE_PAYER_BYTES = pubkeyBytes((byte) 0x52);
    public static final byte[] SOME_MEMO_TX_FEE_PAYER_BYTES = pubkeyBytes((byte) 0x63);
    public static final byte[] SOME_FAILED_TX_OTHER_PUBKEY_BYTES = pubkeyBytes((byte) 0x74);
    public static final byte[] SOME_SENDER_PUBKEY_BYTES = pubkeyBytes((byte) 0x85);
    public static final byte[] SOME_RECEIVER_PUBKEY_BYTES = pubkeyBytes((byte) 0x96);

    public static final String SOME_LARGE_TRANSFER_FEE_PAYER_BASE58 =
            Base58.encode(SOME_LARGE_TRANSFER_FEE_PAYER_BYTES);
    public static final String SOME_FAILED_TX_FEE_PAYER_BASE58 =
            Base58.encode(SOME_FAILED_TX_FEE_PAYER_BYTES);
    public static final String SOME_MEMO_TX_FEE_PAYER_BASE58 =
            Base58.encode(SOME_MEMO_TX_FEE_PAYER_BYTES);
    public static final String SOME_FAILED_TX_OTHER_PUBKEY_BASE58 =
            Base58.encode(SOME_FAILED_TX_OTHER_PUBKEY_BYTES);
    public static final String SOME_SENDER_PUBKEY_BASE58 = Base58.encode(SOME_SENDER_PUBKEY_BYTES);
    public static final String SOME_RECEIVER_PUBKEY_BASE58 = Base58.encode(SOME_RECEIVER_PUBKEY_BYTES);

    public static final String SOME_MEMO_TEXT = "hello prism e2e";

    public static final long SOME_FEE_LAMPORTS = 5_000L;

    public static final long SOME_LARGE_TRANSFER_FEE_PAYER_PRE_LAMPORTS = 1_000_000_000L;
    public static final long SOME_LARGE_TRANSFER_FEE_PAYER_POST_LAMPORTS =
            SOME_LARGE_TRANSFER_FEE_PAYER_PRE_LAMPORTS - SOME_FEE_LAMPORTS;
    public static final long SOME_LARGE_TRANSFER_SENDER_PRE_LAMPORTS = 5_000_000_000L;
    public static final long SOME_LARGE_TRANSFER_AMOUNT_LAMPORTS = 5_000_000_000L;

    public static final long SOME_FAILED_TX_FEE_PAYER_PRE_LAMPORTS = 2_000_000_000L;
    public static final long SOME_FAILED_TX_FEE_PAYER_POST_LAMPORTS =
            SOME_FAILED_TX_FEE_PAYER_PRE_LAMPORTS - SOME_FEE_LAMPORTS;

    public static final long SOME_MEMO_TX_TRANSFER_LAMPORTS = 100_000_000L;
    public static final long SOME_MEMO_TX_FEE_PAYER_PRE_LAMPORTS = 3_000_000_000L;
    public static final long SOME_MEMO_TX_FEE_PAYER_POST_LAMPORTS =
            SOME_MEMO_TX_FEE_PAYER_PRE_LAMPORTS - SOME_MEMO_TX_TRANSFER_LAMPORTS - SOME_FEE_LAMPORTS;
    public static final long SOME_MEMO_TX_FEE_PAYER_DECREASE_LAMPORTS =
            SOME_MEMO_TX_FEE_PAYER_PRE_LAMPORTS - SOME_MEMO_TX_FEE_PAYER_POST_LAMPORTS;

    public static final BigDecimal SOME_LARGE_TRANSFER_AMOUNT_SOL =
            BigDecimal.valueOf(SOME_LARGE_TRANSFER_AMOUNT_LAMPORTS, LAMPORTS_PER_SOL_SCALE);
    public static final BigDecimal SOME_FAILED_TX_AMOUNT_SOL =
            BigDecimal.valueOf(SOME_FEE_LAMPORTS, LAMPORTS_PER_SOL_SCALE);
    public static final BigDecimal SOME_MEMO_TX_AMOUNT_SOL =
            BigDecimal.valueOf(SOME_MEMO_TX_FEE_PAYER_DECREASE_LAMPORTS, LAMPORTS_PER_SOL_SCALE);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private E2eBlockFixture() {}

    public static List<SubscribeUpdateTransaction> grpcUpdates() {
        return List.of(buildGrpcLargeTransfer(), buildGrpcFailedTx(), buildGrpcMemoTx());
    }

    public static String webSocketFrame() {
        var frame = MAPPER.createObjectNode();
        frame.put("jsonrpc", "2.0");
        frame.put("method", "blockNotification");
        var params = MAPPER.createObjectNode();
        params.put("subscription", 0);
        var result = MAPPER.createObjectNode();
        var context = MAPPER.createObjectNode();
        context.put("slot", SOME_SLOT);
        result.set("context", context);
        result.set("value", buildWebSocketBlockValue());
        params.set("result", result);
        frame.set("params", params);
        return frame.toString();
    }

    public static JsonNode webSocketBlockValue() {
        return buildWebSocketBlockValue();
    }

    public static List<SolanaTransaction> expectedDomainTransactions() {
        return List.of(
                SolanaTransaction.builder()
                        .signature(new Signature(SOME_LARGE_TRANSFER_SIGNATURE_BASE58))
                        .slot(SOME_SLOT)
                        .amount(SOME_LARGE_TRANSFER_AMOUNT_SOL)
                        .failed(false)
                        .memo(null)
                        .from(new Pubkey(SolanaAddress.truncate(SOME_SENDER_PUBKEY_BASE58)))
                        .to(new Pubkey(SolanaAddress.truncate(SOME_RECEIVER_PUBKEY_BASE58)))
                        .build(),
                SolanaTransaction.builder()
                        .signature(new Signature(SOME_FAILED_TX_SIGNATURE_BASE58))
                        .slot(SOME_SLOT)
                        .amount(SOME_FAILED_TX_AMOUNT_SOL)
                        .failed(true)
                        .memo(null)
                        .from(new Pubkey(SolanaAddress.truncate(SOME_FAILED_TX_FEE_PAYER_BASE58)))
                        .to(null)
                        .build(),
                SolanaTransaction.builder()
                        .signature(new Signature(SOME_MEMO_TX_SIGNATURE_BASE58))
                        .slot(SOME_SLOT)
                        .amount(SOME_MEMO_TX_AMOUNT_SOL)
                        .failed(false)
                        .memo(SOME_MEMO_TEXT)
                        .from(new Pubkey(SolanaAddress.truncate(SOME_MEMO_TX_FEE_PAYER_BASE58)))
                        .to(new Pubkey(SolanaAddress.truncate(SOME_RECEIVER_PUBKEY_BASE58)))
                        .build());
    }

    public static List<Account> expectedDomainFeePayers() {
        return List.of(
                Account.builder()
                        .pubkey(new Pubkey(SOME_LARGE_TRANSFER_FEE_PAYER_BASE58))
                        .lamports(SOME_LARGE_TRANSFER_FEE_PAYER_POST_LAMPORTS)
                        .slot(SOME_SLOT)
                        .executable(false)
                        .rentEpoch(0L)
                        .build(),
                Account.builder()
                        .pubkey(new Pubkey(SOME_FAILED_TX_FEE_PAYER_BASE58))
                        .lamports(SOME_FAILED_TX_FEE_PAYER_POST_LAMPORTS)
                        .slot(SOME_SLOT)
                        .executable(false)
                        .rentEpoch(0L)
                        .build(),
                Account.builder()
                        .pubkey(new Pubkey(SOME_MEMO_TX_FEE_PAYER_BASE58))
                        .lamports(SOME_MEMO_TX_FEE_PAYER_POST_LAMPORTS)
                        .slot(SOME_SLOT)
                        .executable(false)
                        .rentEpoch(0L)
                        .build());
    }

    private static SubscribeUpdateTransaction buildGrpcLargeTransfer() {
        var keys = List.of(
                SOME_LARGE_TRANSFER_FEE_PAYER_BYTES, SOME_SENDER_PUBKEY_BYTES, SOME_RECEIVER_PUBKEY_BYTES);
        var pre = List.of(
                SOME_LARGE_TRANSFER_FEE_PAYER_PRE_LAMPORTS, SOME_LARGE_TRANSFER_SENDER_PRE_LAMPORTS, 0L);
        var post = List.of(SOME_LARGE_TRANSFER_FEE_PAYER_POST_LAMPORTS, 0L, SOME_LARGE_TRANSFER_AMOUNT_LAMPORTS);
        return buildGrpcTx(SOME_LARGE_TRANSFER_SIGNATURE_BYTES, keys, pre, post, false, null);
    }

    private static SubscribeUpdateTransaction buildGrpcFailedTx() {
        var keys = List.of(SOME_FAILED_TX_FEE_PAYER_BYTES, SOME_FAILED_TX_OTHER_PUBKEY_BYTES);
        var pre = List.of(SOME_FAILED_TX_FEE_PAYER_PRE_LAMPORTS, 0L);
        var post = List.of(SOME_FAILED_TX_FEE_PAYER_POST_LAMPORTS, 0L);
        return buildGrpcTx(SOME_FAILED_TX_SIGNATURE_BYTES, keys, pre, post, true, null);
    }

    private static SubscribeUpdateTransaction buildGrpcMemoTx() {
        var keys = List.of(
                SOME_MEMO_TX_FEE_PAYER_BYTES,
                SOME_RECEIVER_PUBKEY_BYTES,
                GeyserTestFixtures.MEMO_V1_PROGRAM_ID_BYTES);
        var pre = List.of(SOME_MEMO_TX_FEE_PAYER_PRE_LAMPORTS, 0L, 0L);
        var post = List.of(SOME_MEMO_TX_FEE_PAYER_POST_LAMPORTS, SOME_MEMO_TX_TRANSFER_LAMPORTS, 0L);
        return buildGrpcTx(SOME_MEMO_TX_SIGNATURE_BYTES, keys, pre, post, false, SOME_MEMO_TEXT);
    }

    private static SubscribeUpdateTransaction buildGrpcTx(
            byte[] sigBytes,
            List<byte[]> accountKeys,
            List<Long> preBalances,
            List<Long> postBalances,
            boolean failed,
            String memoText) {
        var messageBuilder = Message.newBuilder();
        accountKeys.forEach(key -> messageBuilder.addAccountKeys(ByteString.copyFrom(key)));
        if (memoText != null) {
            messageBuilder.addInstructions(CompiledInstruction.newBuilder()
                    .setProgramIdIndex(accountKeys.size() - 1)
                    .setData(ByteString.copyFrom(memoText.getBytes(StandardCharsets.UTF_8)))
                    .build());
        }
        var transaction = Transaction.newBuilder()
                .addSignatures(ByteString.copyFrom(sigBytes))
                .setMessage(messageBuilder.build())
                .build();
        var metaBuilder = TransactionStatusMeta.newBuilder()
                .addAllPreBalances(preBalances)
                .addAllPostBalances(postBalances);
        if (failed) {
            metaBuilder.setErr(TransactionError.newBuilder()
                    .setErr(ByteString.copyFrom(new byte[] {0x01}))
                    .build());
        }
        var info = SubscribeUpdateTransactionInfo.newBuilder()
                .setSignature(ByteString.copyFrom(sigBytes))
                .setTransaction(transaction)
                .setMeta(metaBuilder.build())
                .build();
        return SubscribeUpdateTransaction.newBuilder()
                .setSlot(SOME_SLOT)
                .setTransaction(info)
                .build();
    }

    private static ObjectNode buildWebSocketBlockValue() {
        var value = MAPPER.createObjectNode();
        value.put("slot", SOME_SLOT);
        var transactions = MAPPER.createArrayNode();
        transactions.add(buildWebSocketLargeTransferTx());
        transactions.add(buildWebSocketFailedTx());
        transactions.add(buildWebSocketMemoTx());
        value.set("transactions", transactions);
        return value;
    }

    private static ObjectNode buildWebSocketLargeTransferTx() {
        return buildWebSocketTx(
                SOME_LARGE_TRANSFER_SIGNATURE_BASE58,
                List.of(
                        SOME_LARGE_TRANSFER_FEE_PAYER_BASE58,
                        SOME_SENDER_PUBKEY_BASE58,
                        SOME_RECEIVER_PUBKEY_BASE58),
                List.of(
                        SOME_LARGE_TRANSFER_FEE_PAYER_PRE_LAMPORTS,
                        SOME_LARGE_TRANSFER_SENDER_PRE_LAMPORTS,
                        0L),
                List.of(
                        SOME_LARGE_TRANSFER_FEE_PAYER_POST_LAMPORTS,
                        0L,
                        SOME_LARGE_TRANSFER_AMOUNT_LAMPORTS),
                false,
                null);
    }

    private static ObjectNode buildWebSocketFailedTx() {
        return buildWebSocketTx(
                SOME_FAILED_TX_SIGNATURE_BASE58,
                List.of(SOME_FAILED_TX_FEE_PAYER_BASE58, SOME_FAILED_TX_OTHER_PUBKEY_BASE58),
                List.of(SOME_FAILED_TX_FEE_PAYER_PRE_LAMPORTS, 0L),
                List.of(SOME_FAILED_TX_FEE_PAYER_POST_LAMPORTS, 0L),
                true,
                null);
    }

    private static ObjectNode buildWebSocketMemoTx() {
        return buildWebSocketTx(
                SOME_MEMO_TX_SIGNATURE_BASE58,
                List.of(
                        SOME_MEMO_TX_FEE_PAYER_BASE58,
                        SOME_RECEIVER_PUBKEY_BASE58,
                        SolanaPrograms.MEMO_V1_PROGRAM_ID),
                List.of(SOME_MEMO_TX_FEE_PAYER_PRE_LAMPORTS, 0L, 0L),
                List.of(SOME_MEMO_TX_FEE_PAYER_POST_LAMPORTS, SOME_MEMO_TX_TRANSFER_LAMPORTS, 0L),
                false,
                SOME_MEMO_TEXT);
    }

    private static ObjectNode buildWebSocketTx(
            String signatureBase58,
            List<String> accountKeys,
            List<Long> preBalances,
            List<Long> postBalances,
            boolean failed,
            String memoText) {
        var tx = MAPPER.createObjectNode();
        var transaction = MAPPER.createObjectNode();
        var signatures = MAPPER.createArrayNode();
        signatures.add(signatureBase58);
        transaction.set("signatures", signatures);
        var message = MAPPER.createObjectNode();
        var keysArray = MAPPER.createArrayNode();
        accountKeys.forEach(key -> {
            var entry = MAPPER.createObjectNode();
            entry.put("pubkey", key);
            keysArray.add(entry);
        });
        message.set("accountKeys", keysArray);
        var instructions = MAPPER.createArrayNode();
        if (memoText != null) {
            var instruction = MAPPER.createObjectNode();
            instruction.put("programId", accountKeys.get(accountKeys.size() - 1));
            instruction.put("parsed", memoText);
            instructions.add(instruction);
        }
        message.set("instructions", instructions);
        transaction.set("message", message);
        tx.set("transaction", transaction);
        var meta = MAPPER.createObjectNode();
        if (failed) {
            var err = MAPPER.createObjectNode();
            err.put("InstructionError", 0);
            meta.set("err", err);
        } else {
            meta.putNull("err");
        }
        meta.set("preBalances", longsArray(preBalances));
        meta.set("postBalances", longsArray(postBalances));
        tx.set("meta", meta);
        return tx;
    }

    private static ArrayNode longsArray(List<Long> values) {
        var array = MAPPER.createArrayNode();
        values.forEach(array::add);
        return array;
    }

    private static byte[] sigBytes(byte sentinel) {
        var bytes = new byte[64];
        bytes[0] = sentinel;
        return bytes;
    }

    private static byte[] pubkeyBytes(byte sentinel) {
        var bytes = new byte[32];
        bytes[0] = sentinel;
        return bytes;
    }
}
