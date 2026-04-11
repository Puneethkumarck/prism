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

    public static final long SLOT = 280_000_042L;

    public static final byte[] LARGE_TRANSFER_SIGNATURE_BYTES = sigBytes((byte) 0xA1);
    public static final byte[] FAILED_TX_SIGNATURE_BYTES = sigBytes((byte) 0xB2);
    public static final byte[] MEMO_TX_SIGNATURE_BYTES = sigBytes((byte) 0xC3);

    public static final String LARGE_TRANSFER_SIGNATURE_BASE58 =
            Base58.encode(LARGE_TRANSFER_SIGNATURE_BYTES);
    public static final String FAILED_TX_SIGNATURE_BASE58 = Base58.encode(FAILED_TX_SIGNATURE_BYTES);
    public static final String MEMO_TX_SIGNATURE_BASE58 = Base58.encode(MEMO_TX_SIGNATURE_BYTES);

    public static final byte[] LARGE_TRANSFER_FEE_PAYER_BYTES = pubkeyBytes((byte) 0x41);
    public static final byte[] FAILED_TX_FEE_PAYER_BYTES = pubkeyBytes((byte) 0x52);
    public static final byte[] MEMO_TX_FEE_PAYER_BYTES = pubkeyBytes((byte) 0x63);
    public static final byte[] FAILED_TX_OTHER_PUBKEY_BYTES = pubkeyBytes((byte) 0x74);
    public static final byte[] SENDER_PUBKEY_BYTES = pubkeyBytes((byte) 0x85);
    public static final byte[] RECEIVER_PUBKEY_BYTES = pubkeyBytes((byte) 0x96);

    public static final String LARGE_TRANSFER_FEE_PAYER_BASE58 =
            Base58.encode(LARGE_TRANSFER_FEE_PAYER_BYTES);
    public static final String FAILED_TX_FEE_PAYER_BASE58 = Base58.encode(FAILED_TX_FEE_PAYER_BYTES);
    public static final String MEMO_TX_FEE_PAYER_BASE58 = Base58.encode(MEMO_TX_FEE_PAYER_BYTES);
    public static final String FAILED_TX_OTHER_PUBKEY_BASE58 =
            Base58.encode(FAILED_TX_OTHER_PUBKEY_BYTES);
    public static final String SENDER_PUBKEY_BASE58 = Base58.encode(SENDER_PUBKEY_BYTES);
    public static final String RECEIVER_PUBKEY_BASE58 = Base58.encode(RECEIVER_PUBKEY_BYTES);

    public static final String MEMO_TEXT = "hello prism e2e";

    public static final long FEE_LAMPORTS = 5_000L;

    public static final long LARGE_TRANSFER_FEE_PAYER_PRE_LAMPORTS = 1_000_000_000L;
    public static final long LARGE_TRANSFER_FEE_PAYER_POST_LAMPORTS =
            LARGE_TRANSFER_FEE_PAYER_PRE_LAMPORTS - FEE_LAMPORTS;
    public static final long LARGE_TRANSFER_SENDER_PRE_LAMPORTS = 5_000_000_000L;
    public static final long LARGE_TRANSFER_AMOUNT_LAMPORTS = 5_000_000_000L;

    public static final long FAILED_TX_FEE_PAYER_PRE_LAMPORTS = 2_000_000_000L;
    public static final long FAILED_TX_FEE_PAYER_POST_LAMPORTS =
            FAILED_TX_FEE_PAYER_PRE_LAMPORTS - FEE_LAMPORTS;

    public static final long MEMO_TX_TRANSFER_LAMPORTS = 100_000_000L;
    public static final long MEMO_TX_FEE_PAYER_PRE_LAMPORTS = 3_000_000_000L;
    public static final long MEMO_TX_FEE_PAYER_POST_LAMPORTS =
            MEMO_TX_FEE_PAYER_PRE_LAMPORTS - MEMO_TX_TRANSFER_LAMPORTS - FEE_LAMPORTS;
    public static final long MEMO_TX_FEE_PAYER_DECREASE_LAMPORTS =
            MEMO_TX_FEE_PAYER_PRE_LAMPORTS - MEMO_TX_FEE_PAYER_POST_LAMPORTS;

    private static final int LAMPORTS_PER_SOL_SCALE = 9;

    public static final BigDecimal LARGE_TRANSFER_AMOUNT_SOL =
            BigDecimal.valueOf(LARGE_TRANSFER_AMOUNT_LAMPORTS, LAMPORTS_PER_SOL_SCALE);
    public static final BigDecimal FAILED_TX_AMOUNT_SOL =
            BigDecimal.valueOf(FEE_LAMPORTS, LAMPORTS_PER_SOL_SCALE);
    public static final BigDecimal MEMO_TX_AMOUNT_SOL =
            BigDecimal.valueOf(MEMO_TX_FEE_PAYER_DECREASE_LAMPORTS, LAMPORTS_PER_SOL_SCALE);

    private E2eBlockFixture() {}

    public static List<SubscribeUpdateTransaction> grpcUpdates() {
        return List.of(buildGrpcLargeTransfer(), buildGrpcFailedTx(), buildGrpcMemoTx());
    }

    public static String webSocketFrame() {
        var mapper = new ObjectMapper();
        var frame = mapper.createObjectNode();
        frame.put("jsonrpc", "2.0");
        frame.put("method", "blockNotification");
        var params = mapper.createObjectNode();
        params.put("subscription", 0);
        var result = mapper.createObjectNode();
        var context = mapper.createObjectNode();
        context.put("slot", SLOT);
        result.set("context", context);
        result.set("value", buildWebSocketBlockValue(mapper));
        params.set("result", result);
        frame.set("params", params);
        return frame.toString();
    }

    public static JsonNode webSocketBlockValue() {
        return buildWebSocketBlockValue(new ObjectMapper());
    }

    public static List<SolanaTransaction> expectedDomainTransactions() {
        return List.of(
                SolanaTransaction.builder()
                        .signature(new Signature(LARGE_TRANSFER_SIGNATURE_BASE58))
                        .slot(SLOT)
                        .amount(LARGE_TRANSFER_AMOUNT_SOL)
                        .failed(false)
                        .memo(null)
                        .from(new Pubkey(SolanaAddress.truncate(SENDER_PUBKEY_BASE58)))
                        .to(new Pubkey(SolanaAddress.truncate(RECEIVER_PUBKEY_BASE58)))
                        .build(),
                SolanaTransaction.builder()
                        .signature(new Signature(FAILED_TX_SIGNATURE_BASE58))
                        .slot(SLOT)
                        .amount(FAILED_TX_AMOUNT_SOL)
                        .failed(true)
                        .memo(null)
                        .from(new Pubkey(SolanaAddress.truncate(FAILED_TX_FEE_PAYER_BASE58)))
                        .to(null)
                        .build(),
                SolanaTransaction.builder()
                        .signature(new Signature(MEMO_TX_SIGNATURE_BASE58))
                        .slot(SLOT)
                        .amount(MEMO_TX_AMOUNT_SOL)
                        .failed(false)
                        .memo(MEMO_TEXT)
                        .from(new Pubkey(SolanaAddress.truncate(MEMO_TX_FEE_PAYER_BASE58)))
                        .to(new Pubkey(SolanaAddress.truncate(RECEIVER_PUBKEY_BASE58)))
                        .build());
    }

    public static List<Account> expectedDomainFeePayers() {
        return List.of(
                Account.builder()
                        .pubkey(new Pubkey(LARGE_TRANSFER_FEE_PAYER_BASE58))
                        .lamports(LARGE_TRANSFER_FEE_PAYER_POST_LAMPORTS)
                        .slot(SLOT)
                        .executable(false)
                        .rentEpoch(0L)
                        .build(),
                Account.builder()
                        .pubkey(new Pubkey(FAILED_TX_FEE_PAYER_BASE58))
                        .lamports(FAILED_TX_FEE_PAYER_POST_LAMPORTS)
                        .slot(SLOT)
                        .executable(false)
                        .rentEpoch(0L)
                        .build(),
                Account.builder()
                        .pubkey(new Pubkey(MEMO_TX_FEE_PAYER_BASE58))
                        .lamports(MEMO_TX_FEE_PAYER_POST_LAMPORTS)
                        .slot(SLOT)
                        .executable(false)
                        .rentEpoch(0L)
                        .build());
    }

    private static SubscribeUpdateTransaction buildGrpcLargeTransfer() {
        var keys = List.of(LARGE_TRANSFER_FEE_PAYER_BYTES, SENDER_PUBKEY_BYTES, RECEIVER_PUBKEY_BYTES);
        var pre = List.of(LARGE_TRANSFER_FEE_PAYER_PRE_LAMPORTS, LARGE_TRANSFER_SENDER_PRE_LAMPORTS, 0L);
        var post = List.of(LARGE_TRANSFER_FEE_PAYER_POST_LAMPORTS, 0L, LARGE_TRANSFER_AMOUNT_LAMPORTS);
        return buildGrpcTx(LARGE_TRANSFER_SIGNATURE_BYTES, keys, pre, post, false, null);
    }

    private static SubscribeUpdateTransaction buildGrpcFailedTx() {
        var keys = List.of(FAILED_TX_FEE_PAYER_BYTES, FAILED_TX_OTHER_PUBKEY_BYTES);
        var pre = List.of(FAILED_TX_FEE_PAYER_PRE_LAMPORTS, 0L);
        var post = List.of(FAILED_TX_FEE_PAYER_POST_LAMPORTS, 0L);
        return buildGrpcTx(FAILED_TX_SIGNATURE_BYTES, keys, pre, post, true, null);
    }

    private static SubscribeUpdateTransaction buildGrpcMemoTx() {
        var keys = List.of(
                MEMO_TX_FEE_PAYER_BYTES, RECEIVER_PUBKEY_BYTES, GeyserTestFixtures.MEMO_V1_PROGRAM_ID_BYTES);
        var pre = List.of(MEMO_TX_FEE_PAYER_PRE_LAMPORTS, 0L, 0L);
        var post = List.of(MEMO_TX_FEE_PAYER_POST_LAMPORTS, MEMO_TX_TRANSFER_LAMPORTS, 0L);
        return buildGrpcTx(MEMO_TX_SIGNATURE_BYTES, keys, pre, post, false, MEMO_TEXT);
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
                .setSlot(SLOT)
                .setTransaction(info)
                .build();
    }

    private static ObjectNode buildWebSocketBlockValue(ObjectMapper mapper) {
        var value = mapper.createObjectNode();
        value.put("slot", SLOT);
        var transactions = mapper.createArrayNode();
        transactions.add(buildWebSocketLargeTransferTx(mapper));
        transactions.add(buildWebSocketFailedTx(mapper));
        transactions.add(buildWebSocketMemoTx(mapper));
        value.set("transactions", transactions);
        return value;
    }

    private static ObjectNode buildWebSocketLargeTransferTx(ObjectMapper mapper) {
        return buildWebSocketTx(
                mapper,
                LARGE_TRANSFER_SIGNATURE_BASE58,
                List.of(LARGE_TRANSFER_FEE_PAYER_BASE58, SENDER_PUBKEY_BASE58, RECEIVER_PUBKEY_BASE58),
                List.of(LARGE_TRANSFER_FEE_PAYER_PRE_LAMPORTS, LARGE_TRANSFER_SENDER_PRE_LAMPORTS, 0L),
                List.of(LARGE_TRANSFER_FEE_PAYER_POST_LAMPORTS, 0L, LARGE_TRANSFER_AMOUNT_LAMPORTS),
                false,
                null);
    }

    private static ObjectNode buildWebSocketFailedTx(ObjectMapper mapper) {
        return buildWebSocketTx(
                mapper,
                FAILED_TX_SIGNATURE_BASE58,
                List.of(FAILED_TX_FEE_PAYER_BASE58, FAILED_TX_OTHER_PUBKEY_BASE58),
                List.of(FAILED_TX_FEE_PAYER_PRE_LAMPORTS, 0L),
                List.of(FAILED_TX_FEE_PAYER_POST_LAMPORTS, 0L),
                true,
                null);
    }

    private static ObjectNode buildWebSocketMemoTx(ObjectMapper mapper) {
        return buildWebSocketTx(
                mapper,
                MEMO_TX_SIGNATURE_BASE58,
                List.of(MEMO_TX_FEE_PAYER_BASE58, RECEIVER_PUBKEY_BASE58, SolanaPrograms.MEMO_V1_PROGRAM_ID),
                List.of(MEMO_TX_FEE_PAYER_PRE_LAMPORTS, 0L, 0L),
                List.of(MEMO_TX_FEE_PAYER_POST_LAMPORTS, MEMO_TX_TRANSFER_LAMPORTS, 0L),
                false,
                MEMO_TEXT);
    }

    private static ObjectNode buildWebSocketTx(
            ObjectMapper mapper,
            String signatureBase58,
            List<String> accountKeys,
            List<Long> preBalances,
            List<Long> postBalances,
            boolean failed,
            String memoText) {
        var tx = mapper.createObjectNode();
        var transaction = mapper.createObjectNode();
        var signatures = mapper.createArrayNode();
        signatures.add(signatureBase58);
        transaction.set("signatures", signatures);
        var message = mapper.createObjectNode();
        var keysArray = mapper.createArrayNode();
        accountKeys.forEach(key -> {
            var entry = mapper.createObjectNode();
            entry.put("pubkey", key);
            keysArray.add(entry);
        });
        message.set("accountKeys", keysArray);
        var instructions = mapper.createArrayNode();
        if (memoText != null) {
            var instruction = mapper.createObjectNode();
            instruction.put("programId", accountKeys.get(accountKeys.size() - 1));
            instruction.put("parsed", memoText);
            instructions.add(instruction);
        }
        message.set("instructions", instructions);
        transaction.set("message", message);
        tx.set("transaction", transaction);
        var meta = mapper.createObjectNode();
        if (failed) {
            var err = mapper.createObjectNode();
            err.put("InstructionError", 0);
            meta.set("err", err);
        } else {
            meta.putNull("err");
        }
        meta.set("preBalances", longsArray(mapper, preBalances));
        meta.set("postBalances", longsArray(mapper, postBalances));
        tx.set("meta", meta);
        return tx;
    }

    private static ArrayNode longsArray(ObjectMapper mapper, List<Long> values) {
        var array = mapper.createArrayNode();
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
